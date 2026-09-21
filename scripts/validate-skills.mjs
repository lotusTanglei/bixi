#!/usr/bin/env node
// Bounded package checks, not a general YAML parser or comprehensive secret scanner.
import { lstatSync, readFileSync, readdirSync, statSync } from 'node:fs';
import { dirname, isAbsolute, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const skillNames = ['context', 'feature', 'debug', 'architecture', 'verify'].map(name => `bixi-${name}`);
const requiredDocuments = [
  'skills/README.md', 'skills/evals/README.md', 'skills/evals/context-search.md',
  'skills/evals/feature-crud.md', 'skills/evals/debug-regression.md',
  'skills/evals/architecture-boundary.md', 'skills/evals/dual-mode-verification.md',
];
// Keep this deliberate list in step with the reference implementation and guide.
const projectReferences = [
  ['AGENTS.md', 'file'],
  ['.docs/1_ARCHITECTURE.md', 'file'],
  ['.docs/5_AI_DEVELOPMENT.md', 'file'],
  ['bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo', 'directory'],
  ['bixi-ui/src/views/demo/task', 'directory'],
  ['bixi-ui/src/api/demo/task.ts', 'file'],
  ['bixi-project-documents/sql/01_init_all_tables.sql', 'file'],
  ['bixi-project-documents/sql/03_add_indexes.sql', 'file'],
  ['bixi-project-documents/sql/04_init_data.sql', 'file'],
  ['scripts/acceptance.mjs', 'file'],
];
const requiredTargets = [
  'architecture-check', 'runtime-config-check', 'backend-cloud-ci', 'backend-single-ci',
  'frontend-ci', 'start-cloud', 'verify-cloud', 'start-single', 'verify-single',
];

function contained(base, candidate) {
  const path = relative(base, candidate);
  return path !== '..' && !path.startsWith(`..${sep}`) && !isAbsolute(path);
}

function exists(path, kind) {
  try {
    const stat = statSync(path);
    return kind === 'file' ? stat.isFile() : kind === 'directory' ? stat.isDirectory() : true;
  } catch {
    return false;
  }
}

function placeholder(value) {
  return /^(?:\$\{[^}]+\}|\$[A-Za-z_][\w]*|<[^>]+>|\[[^\]]+\]|\*+|\.{3}|(?:your[-_].*|example|placeholder|redacted|not[-_]set|dummy|changeme))$/i.test(value);
}

function hasSecret(contents) {
  if (/-----BEGIN (?:[A-Z]+ )*PRIVATE KEY-----/.test(contents)) return true;
  if (/\b(?:sk-[A-Za-z0-9_-]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[A-Z0-9]{16})\b/.test(contents)) return true;
  // Only obvious assignments; names alone and protective .env mentions are allowed.
  const assignments = /\b([A-Za-z_][\w-]*)["']?\s*[:=]\s*(?:"([^"\r\n]*)"|'([^'\r\n]*)'|(`?)([^\s`,;]+))/g;
  for (const match of contents.matchAll(assignments)) {
    const name = match[1].replace(/([a-z])([A-Z])/g, '$1_$2');
    if (!/(?:^|[_-])(?:password|passwd|token|secret|api[_-]?key)(?:$|[_-])/i.test(name)) continue;
    const value = match[2] ?? match[3] ?? match[5];
    if (value && !placeholder(value)) return true;
  }
  return false;
}

function markdownDestination(contents, start) {
  const angle = contents[start] === '<';
  let cursor = start + (angle ? 1 : 0);
  let depth = 0;
  let value = '';
  for (; cursor < contents.length; cursor++) {
    const character = contents[cursor];
    if (character === '\r' || character === '\n') break;
    if (character === '\\' && /[\\()<>]/.test(contents[cursor + 1] ?? '')) {
      value += contents[++cursor];
      continue;
    }
    if (angle) {
      if (character === '>') return value ? { value, end: cursor + 1 } : null;
    } else {
      if (/\s/.test(character) || (character === ')' && depth === 0)) break;
      if (character === '(') depth++;
      if (character === ')') depth--;
    }
    value += character;
  }
  return angle || depth || !value ? null : { value, end: cursor };
}

function markdownDestinations(contents) {
  // A bounded link reader: inline links/images, reference definitions, quoted
  // titles, angle destinations, and balanced/escaped destination parentheses.
  const destinations = [];
  const inline = /!?\[[^\]\r\n]*\]\([ \t]*/g;
  const definitions = /^[ \t]{0,3}\[[^\]\r\n]+\]:[ \t]*/gm;
  for (const expression of [inline, definitions]) {
    for (const match of contents.matchAll(expression)) {
      const destination = markdownDestination(contents, match.index + match[0].length);
      if (!destination) continue;
      if (expression === inline && !/^[ \t]*(?:(?:"(?:\\.|[^"\\\r\n])*"|'(?:\\.|[^'\\\r\n])*')[ \t]*)?\)/.test(contents.slice(destination.end))) continue;
      destinations.push(destination.value);
    }
  }
  return destinations;
}

function remoteDestination(destination) {
  // Only HTTP(S) and protocol-relative network URLs are external references.
  // file:, Windows drives, mailto:, custom schemes, and backslash paths are not.
  if (!/^(?:https?:\/\/|\/\/[^/\\])/i.test(destination) || destination.includes('\\')) return false;
  try {
    return Boolean(new URL(destination.startsWith('//') ? `https:${destination}` : destination).hostname);
  } catch {
    return false;
  }
}

/** Return all package diagnostics. No writes, subprocesses, or matched contents. */
export function validate(root) {
  const repository = resolve(root);
  const errors = [];
  const add = (code, path, message) => errors.push({ code, path, message });
  const documents = new Map();

  function visit(path) {
    let stat;
    try {
      stat = lstatSync(join(repository, path));
    } catch {
      add('READ_ERROR', path, 'Package path cannot be read.');
      return;
    }
    if (stat.isSymbolicLink()) {
      add('SYMBOLIC_LINK', path, 'Distributable files must not be symbolic links.');
      return;
    }
    if (stat.isDirectory()) {
      let entries;
      try {
        entries = readdirSync(join(repository, path)).sort();
      } catch {
        add('READ_ERROR', path, 'Package directory cannot be read.');
        return;
      }
      for (const entry of entries) visit(`${path}/${entry}`);
    } else if (stat.isFile()) {
      try {
        documents.set(path, readFileSync(join(repository, path), 'utf8'));
      } catch {
        add('READ_ERROR', path, 'Package file cannot be read.');
      }
    }
  }

  visit('skills');
  for (const path of [...requiredDocuments, ...skillNames.map(name => `skills/${name}/SKILL.md`)]) {
    if (!documents.has(path)) add('MISSING_FILE', path, 'Required package file is missing or unreadable.');
  }

  const names = new Set();
  for (const expected of skillNames) {
    const path = `skills/${expected}/SKILL.md`;
    const contents = documents.get(path);
    if (contents === undefined) continue;
    const frontmatter = /^---\r?\n([\s\S]*?)\r?\n---(?:\r?\n|$)([\s\S]*)$/.exec(contents);
    if (!frontmatter) {
      add('FRONTMATTER', path, 'Use a delimited frontmatter block with quoted name and description.');
      continue;
    }
    const fields = {};
    let malformed = false;
    for (const line of frontmatter[1].split(/\r?\n/)) {
      const field = /^(name|description):\s*("(?:[^"\\]|\\.)*")\s*$/.exec(line);
      if (!field || Object.hasOwn(fields, field[1])) {
        malformed = true;
        continue;
      }
      try {
        fields[field[1]] = JSON.parse(field[2]);
      } catch {
        malformed = true;
      }
    }
    if (malformed || Object.keys(fields).length !== 2) {
      add('FRONTMATTER', path, 'Frontmatter must contain exactly name and description as JSON-compatible quoted strings.');
    }
    if (fields.name !== undefined) {
      if (fields.name !== expected) add('SKILL_NAME', path, 'Skill name must match its directory name.');
      if (names.has(fields.name)) add('DUPLICATE_NAME', path, 'Skill names must be unique.');
      names.add(fields.name);
    }
    if (fields.description !== undefined && (!fields.description.trim() || fields.description.length > 1024)) {
      add('DESCRIPTION', path, 'Description must contain 1 to 1024 characters and not be blank.');
    }
    if (!frontmatter[2].trim()) add('EMPTY_BODY', path, 'Skill body must not be empty.');
  }

  for (const [path, kind] of projectReferences) {
    if (!exists(join(repository, path), kind)) add('PROJECT_REFERENCE', path, 'Required Bixi reference is missing or has the wrong type.');
  }
  let makefile = '';
  try {
    makefile = readFileSync(join(repository, 'Makefile'), 'utf8');
  } catch {
    add('READ_ERROR', 'Makefile', 'Makefile cannot be read.');
  }
  const targets = new Set();
  for (const match of makefile.matchAll(/^([A-Za-z0-9_.%/-]+(?:[ ]+[A-Za-z0-9_.%/-]+)*):(?!=)/gm)) {
    for (const target of match[1].split(/ +/)) targets.add(target);
  }
  for (const target of requiredTargets) {
    if (!targets.has(target)) add('MAKE_TARGET', 'Makefile', `Required Make target is missing: ${target}.`);
  }

  for (const [path, contents] of documents) {
    if (/\b(?:TODO|TBD|FIXME|CHANGEME)\b|\[(?:insert|replace)\b[^\]\r\n]*\]/i.test(contents)) {
      add('SCAFFOLD', path, 'Remove unfinished scaffold markers.');
    }
    if (hasSecret(contents)) add('SECRET_CONTENT', path, 'Possible credential content detected; matched text is omitted.');
    if (/\/(?:Users|home)\/[^\s/`"'<>]+|[A-Za-z]:[\\/]Users[\\/][^\s\\/`"'<>]+/.test(contents)) {
      add('PERSONAL_PATH', path, 'Personal absolute path detected; matched text is omitted.');
    }
    if (!path.endsWith('.md')) continue;

    // Explicit repository references are separate from installable, skill-local links.
    for (const match of contents.matchAll(/<!--\s*bixi-ref:\s*([^\r\n]*?)\s*-->/g)) {
      const target = match[1].trim();
      const candidate = resolve(repository, target);
      if (!target || isAbsolute(target) || !contained(repository, candidate) || !exists(candidate)) {
        add('PROJECT_REFERENCE', path, 'Declared repository reference is missing or outside the repository.');
      }
    }
    for (const match of contents.matchAll(/<!--\s*bixi-make:\s*([^\r\n]*?)\s*-->/g)) {
      if (!targets.has(match[1].trim())) add('MAKE_TARGET', path, 'Declared Make target does not exist.');
    }
    const skillName = path.split('/')[1];
    const linkBoundary = join(repository, 'skills', skillNames.includes(skillName) ? skillName : '');
    for (const destination of markdownDestinations(contents)) {
      if (destination.startsWith('#') || remoteDestination(destination)) continue;
      let target;
      try {
        target = decodeURIComponent(destination.split(/[?#]/)[0]);
      } catch {
        add('LOCAL_LINK', path, 'Local Markdown link has invalid URL encoding.');
        continue;
      }
      const candidate = resolve(dirname(join(repository, path)), target);
      if (/^[a-z][a-z0-9+.-]*:/i.test(target) || target.includes('\\')) {
        add('LOCAL_LINK', path, 'Only HTTP(S) URLs, anchors, and distributable relative paths are supported.');
      } else if (isAbsolute(target) || !contained(linkBoundary, candidate) || !exists(candidate)) {
        add('LOCAL_LINK', path, 'Local Markdown link is missing or outside its distributable directory.');
      }
    }
  }
  return errors;
}

const scriptPath = fileURLToPath(import.meta.url);
if (process.argv[1] && resolve(process.argv[1]) === scriptPath) {
  const errors = validate(process.argv[2] ?? resolve(dirname(scriptPath), '..'));
  for (const error of errors) console.error(`${error.code} ${error.path}: ${error.message}`);
  if (errors.length) {
    console.error(`Skill validation failed with ${errors.length} error(s).`);
    process.exitCode = 1;
  } else {
    console.log('Skill package validation passed.');
  }
}
