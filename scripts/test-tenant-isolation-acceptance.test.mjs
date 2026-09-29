import assert from 'node:assert/strict';
import { execFile as execFileCallback } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { promisify } from 'node:util';
import test from 'node:test';

import { extractWorkbookText } from './tenant-isolation-support.mjs';

const execFile = promisify(execFileCallback);

test('extracts inline XLSX cell text for tenant export assertions', async () => {
	const fixtureRoot = await mkdtemp(join(tmpdir(), 'bixi-tenant-export-'));
	try {
		await mkdir(join(fixtureRoot, 'xl', 'worksheets'), { recursive: true });
		await writeFile(join(fixtureRoot, 'xl', 'worksheets', 'sheet1.xml'), `
<worksheet><sheetData><row>
<c t="inlineStr"><is><t>Tenant two marker</t></is></c>
<c t="inlineStr"><is><t>tenant_matrix_user</t></is></c>
</row></sheetData></worksheet>`);
		const workbook = join(fixtureRoot, 'users.xlsx');
		await execFile('zip', ['-q', '-r', workbook, 'xl'], { cwd: fixtureRoot });

		const text = await extractWorkbookText(await readFile(workbook));

		assert.match(text, /Tenant two marker/);
		assert.match(text, /tenant_matrix_user/);
		assert.doesNotMatch(text, /Tenant one marker/);
	}
	finally {
		await rm(fixtureRoot, { recursive: true, force: true });
	}
});

test('keeps BIGINT AI document ids as JSON strings', async () => {
	const source = await readFile(new URL('./ai-tenant-isolation-acceptance.mjs', import.meta.url), 'utf8');
	assert.doesNotMatch(source, /Number\(document[AB]\)/,
		'19 digit document ids must not pass through JavaScript Number');
	assert.match(source, /documentIds: \[documentA\]/);
	assert.match(source, /documentIds: \[documentB\]/);
});
