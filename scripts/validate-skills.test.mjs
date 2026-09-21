import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { copyFileSync, mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';

const validatorUrl = new URL('./validate-skills.mjs', import.meta.url);
const validatorPath = fileURLToPath(validatorUrl);
const skillNames = ['context', 'feature', 'debug', 'architecture', 'verify'].map(name => `bixi-${name}`);
const projectFiles = [
  'AGENTS.md', '.docs/1_ARCHITECTURE.md', '.docs/5_AI_DEVELOPMENT.md',
  'bixi-ui/src/api/demo/task.ts', 'bixi-project-documents/sql/01_init_all_tables.sql',
  'bixi-project-documents/sql/03_add_indexes.sql', 'bixi-project-documents/sql/04_init_data.sql',
  'scripts/acceptance.mjs',
];
const projectDirectories = [
  'bixi-module/bixi-upms-biz/src/main/java/com/lotus/bixi/upms/demo',
  'bixi-ui/src/views/demo/task',
];
const targets = [
  'architecture-check', 'runtime-config-check', 'backend-cloud-ci', 'backend-single-ci',
  'frontend-ci', 'start-cloud', 'verify-cloud', 'start-single', 'verify-single',
];

function put(root, path, contents) {
  mkdirSync(dirname(join(root, path)), { recursive: true });
  writeFileSync(join(root, path), contents);
}

function skill(name, body = '# Workflow\n\nFollow the project contract.') {
  return `---\nname: ${JSON.stringify(name)}\ndescription: "Use when working on this Bixi concern."\n---\n${body}\n`;
}

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'bixi-skills-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  for (const name of skillNames) put(root, `skills/${name}/SKILL.md`, skill(name));
  for (const path of projectFiles) put(root, path, 'project fixture\n');
  for (const path of projectDirectories) mkdirSync(join(root, path), { recursive: true });
  for (const path of [
    'README.md', 'evals/README.md', 'evals/context-search.md', 'evals/feature-crud.md',
    'evals/debug-regression.md', 'evals/architecture-boundary.md', 'evals/dual-mode-verification.md',
  ]) put(root, `skills/${path}`, '# Package documentation\n');
  put(root, 'Makefile', targets.map(target => `${target}:\n\t@true\n`).join('\n'));
  return root;
}

async function validate(root) {
  let module;
  try {
    module = await import(validatorUrl.href);
  } catch (error) {
    assert.fail(`Validator module must load (${error.code ?? error.name}).`);
  }
  assert.equal(typeof module.validate, 'function');
  const errors = await module.validate(root);
  assert.ok(Array.isArray(errors));
  for (const error of errors) {
    assert.deepEqual(Object.keys(error).sort(), ['code', 'message', 'path']);
    for (const field of Object.values(error)) assert.equal(typeof field, 'string');
  }
  return errors;
}

function has(errors, code, path) {
  assert.ok(errors.some(error => error.code === code && (!path || error.path === path)), `Expected ${code} at ${path ?? 'any path'}`);
}

function snapshot(root) {
  const entries = [];
  function visit(directory, prefix = '') {
    for (const entry of readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const relative = `${prefix}${entry.name}`;
      if (entry.isDirectory()) {
        entries.push(`${relative}/`);
        visit(join(directory, entry.name), `${relative}/`);
      } else {
        entries.push(`${relative}:${createHash('sha256').update(readFileSync(join(directory, entry.name))).digest('hex')}`);
      }
    }
  }
  visit(root);
  return entries;
}

test('valid package supports quoted scalars, local links, and declared project references', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/references/guide.md', '# Additional context\n');
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    '# Context', '[Guide](references/guide.md#usage)',
    '[Web](https://example.com/reference)',
    '<!-- bixi-ref: AGENTS.md -->', '<!-- bixi-make: architecture-check -->',
  ].join('\n')));
  assert.deepEqual(await validate(root), []);
});

test('missing package files aggregate structured errors', async t => {
  const root = fixture(t);
  const missing = ['skills/bixi-context', 'skills/README.md', 'skills/evals/feature-crud.md'];
  for (const path of missing) rmSync(join(root, path), { recursive: true });
  const errors = await validate(root);
  has(errors, 'MISSING_FILE', 'skills/bixi-context/SKILL.md');
  has(errors, 'MISSING_FILE', 'skills/README.md');
  has(errors, 'MISSING_FILE', 'skills/evals/feature-crud.md');
});

test('frontmatter rejects malformed, unquoted, duplicate, and extra fields', async t => {
  const root = fixture(t);
  const invalid = [
    'name: "bixi-context"\ndescription: "Use when needed."\n---\nBody',
    '---\nname: bixi-context\ndescription: "Use when needed."\n---\nBody',
    '---\nname: "bixi-context"\ndescription: "Unclosed\n---\nBody',
    '---\nname: "bixi-context"\nname: "bixi-context"\ndescription: "Use when needed."\n---\nBody',
    '---\nname: "bixi-context"\ndescription: "Use when needed."\nextra: "unsupported"\n---\nBody',
  ];
  for (const contents of invalid) {
    put(root, 'skills/bixi-context/SKILL.md', contents);
    has(await validate(root), 'FRONTMATTER', 'skills/bixi-context/SKILL.md');
  }
});

test('name must match folder and be unique', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-feature'));
  const errors = await validate(root);
  has(errors, 'SKILL_NAME');
  has(errors, 'DUPLICATE_NAME');
});

test('description must be nonempty and at most 1024 characters', async t => {
  const root = fixture(t);
  for (const description of [' ', 'x'.repeat(1025)]) {
    put(root, 'skills/bixi-context/SKILL.md', `---\nname: "bixi-context"\ndescription: ${JSON.stringify(description)}\n---\nBody\n`);
    has(await validate(root), 'DESCRIPTION');
  }
});

test('empty bodies and unfinished scaffold markers fail', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', ' \n'));
  put(root, 'skills/bixi-feature/SKILL.md', skill('bixi-feature', '[TODO: Complete this workflow]'));
  const errors = await validate(root);
  has(errors, 'EMPTY_BODY', 'skills/bixi-context/SKILL.md');
  has(errors, 'SCAFFOLD', 'skills/bixi-feature/SKILL.md');
});

test('required project references and Make targets are checked without declarations', async t => {
  const root = fixture(t);
  rmSync(join(root, 'scripts/acceptance.mjs'));
  put(root, 'Makefile', targets.filter(target => target !== 'verify-single').map(target => `${target}:\n`).join(''));
  const errors = await validate(root);
  has(errors, 'PROJECT_REFERENCE', 'scripts/acceptance.mjs');
  has(errors, 'MAKE_TARGET', 'Makefile');
});

test('declarations reject missing references, absent Make targets, and repository escapes', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    '<!-- bixi-ref: absent/file.md -->', '<!-- bixi-ref: ../outside.md -->',
    '<!-- bixi-make: absent-command -->',
  ].join('\n')));
  const errors = await validate(root);
  has(errors, 'PROJECT_REFERENCE', 'skills/bixi-context/SKILL.md');
  has(errors, 'MAKE_TARGET', 'skills/bixi-context/SKILL.md');
  assert.equal(errors.filter(error => error.code === 'PROJECT_REFERENCE').length, 2);
});

test('relative links must exist and remain inside their installable skill', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    '[Missing](references/missing.md)', '[Sibling](../bixi-feature/SKILL.md)',
    '[Missing reference][missing]', '', '[missing]: references/absent.md',
  ].join('\n')));
  assert.equal((await validate(root)).filter(error => error.code === 'LOCAL_LINK').length, 3);
});

test('local file URLs and Windows paths cannot bypass the skill link boundary', async t => {
  const root = fixture(t);
  for (const destination of [
    'file:///opt/project/guide.md', 'C:/opt/project/guide.md', 'C:\\opt\\project\\guide.md',
    'C%3A%2Fopt%2Fproject%2Fguide.md', '\\\\server\\share\\guide.md',
  ]) {
    put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', `[Guide](${destination})`));
    has(await validate(root), 'LOCAL_LINK', 'skills/bixi-context/SKILL.md');
  }
});

test('only HTTP(S), network URLs, and anchors bypass local file checks', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    '[HTTPS](https://example.com/guide)', '[HTTP](http://example.com/guide)',
    '[Network](//example.com/guide)', '[Section](#guide)',
  ].join('\n')));
  assert.deepEqual(await validate(root), []);
  for (const destination of ['mailto:person@example.com', 'ftp://example.com/guide', 'custom:guide', 'https://']) {
    put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', `[Guide](${destination})`));
    has(await validate(root), 'LOCAL_LINK', 'skills/bixi-context/SKILL.md');
  }
});

test('balanced parentheses, angle destinations, titles, and reference links resolve correctly', async t => {
  const root = fixture(t);
  for (const name of ['guide(v2).md', 'guide(v(2)).md', 'guide v2.md']) {
    put(root, `skills/bixi-context/references/${name}`, '# Reference\n');
  }
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    '[Plain](references/guide(v2).md)',
    '[Nested](references/guide(v(2)).md#usage)',
    '[Escaped](references/guide\\(v2\\).md)',
    '[Titled](references/guide(v2).md "Guide title")',
    "[Single quotes](references/guide(v2).md 'Guide title')",
    '[Angle](<references/guide v2.md> "Guide title")',
    '[Reference][guide]', '[Angle reference][angle]',
    '[guide]: references/guide(v2).md "Guide title"',
    "[angle]: <references/guide v2.md> 'Guide title'",
  ].join('\n')));
  assert.deepEqual(await validate(root), []);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', '[Missing](references/missing(v2).md "Missing title")'));
  has(await validate(root), 'LOCAL_LINK', 'skills/bixi-context/SKILL.md');
});

test('protective instructions and placeholder credentials are accepted', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    'Never read or print `.env`; do not edit `.docs/.chiwen.state.json`.',
    'Use `DEMO_PASSWORD=${DEMO_PASSWORD}` and `token=<redacted>`.',
    'Environment variables include AWS_SECRET_ACCESS_KEY; permission names include demo_task_view.',
    'Install under `~/.codex/skills/`.',
  ].join('\n')));
  assert.deepEqual(await validate(root), []);
});

test('secret and personal path findings redact matched contents', async t => {
  const root = fixture(t);
  const secret = 'sensitiveValueForFixture42';
  const personal = '/Users/fixture-person/projects/secret-project';
  put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', [
    `AWS_SECRET_ACCESS_KEY=${secret}`, `password="${secret}"`, `token: ${secret}`,
    `ghp_${secret}`, `sk-${secret}`, '-----BEGIN PRIVATE KEY-----', personal,
  ].join('\n')));
  const errors = await validate(root);
  has(errors, 'SECRET_CONTENT');
  has(errors, 'PERSONAL_PATH');
  assert.ok(!JSON.stringify(errors).includes(secret));
  assert.ok(!JSON.stringify(errors).includes(personal));
});

test('each supported credential form is rejected independently', async t => {
  const root = fixture(t);
  const secret = 'independentCredentialFixture42';
  for (const contents of [
    `AWS_SECRET_ACCESS_KEY=${secret}`, `password="${secret}"`, `token: ${secret}`,
    `{"api_key": "${secret}"}`, `accessToken = '${secret}'`,
    `ghp_${secret}`, `sk-${secret}`, '-----BEGIN RSA PRIVATE KEY-----',
  ]) {
    put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', contents));
    const errors = await validate(root);
    has(errors, 'SECRET_CONTENT');
    assert.ok(!JSON.stringify(errors).includes(secret));
  }
});

test('personal directory paths are rejected on macOS, Linux, and Windows', async t => {
  const root = fixture(t);
  for (const contents of ['/Users/fixture/project', '/home/fixture/project', 'C:\\Users\\fixture\\project']) {
    put(root, 'skills/bixi-context/SKILL.md', skill('bixi-context', contents));
    const errors = await validate(root);
    has(errors, 'PERSONAL_PATH');
    assert.ok(!JSON.stringify(errors).includes(contents));
  }
});

test('safety scan covers supporting files and does not follow symbolic links', async t => {
  const root = fixture(t);
  put(root, 'skills/bixi-debug/references/unsafe.md', 'token=privateFixtureToken42\n');
  symlinkSync(join(root, 'AGENTS.md'), join(root, 'skills/bixi-debug/references/linked.md'));
  const errors = await validate(root);
  has(errors, 'SECRET_CONTENT', 'skills/bixi-debug/references/unsafe.md');
  has(errors, 'SYMBOLIC_LINK', 'skills/bixi-debug/references/linked.md');
});

test('CLI succeeds without changing the fixture and uses optional root argument', t => {
  const root = fixture(t);
  const before = snapshot(root);
  const result = spawnSync(process.execPath, [validatorPath, root], { encoding: 'utf8', cwd: tmpdir() });
  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(snapshot(root), before);
});

test('CLI defaults to its own repository root, independent of current directory', t => {
  const root = fixture(t);
  copyFileSync(validatorPath, join(root, 'scripts/validate-skills.mjs'));
  const result = spawnSync(process.execPath, [join(root, 'scripts/validate-skills.mjs')], { encoding: 'utf8', cwd: tmpdir() });
  assert.equal(result.status, 0, result.stderr);
});

test('CLI aggregates failures, redacts values, and makes no writes', t => {
  const root = fixture(t);
  const secret = 'privateCliFixtureValue42';
  put(root, 'skills/bixi-context/SKILL.md', skill('incorrect-name', `token=${secret}`));
  rmSync(join(root, 'skills/evals/context-search.md'));
  const before = snapshot(root);
  const result = spawnSync(process.execPath, [validatorPath, root], { encoding: 'utf8' });
  assert.equal(result.status, 1);
  const output = result.stdout + result.stderr;
  for (const code of ['SKILL_NAME', 'SECRET_CONTENT', 'MISSING_FILE']) assert.ok(output.includes(code));
  assert.ok(!output.includes(secret));
  assert.deepEqual(snapshot(root), before);
});

test('importing validator is silent and has no filesystem side effects', t => {
  const root = fixture(t);
  const before = snapshot(root);
  const result = spawnSync(process.execPath, ['--input-type=module', '-e', `await import(${JSON.stringify(validatorUrl.href)});`], {
    encoding: 'utf8', cwd: root,
  });
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, '');
  assert.equal(result.stderr, '');
  assert.deepEqual(snapshot(root), before);
});
