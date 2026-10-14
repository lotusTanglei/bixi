import assert from 'node:assert/strict';
import { execFile as execFileCallback, spawn } from 'node:child_process';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
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

test('native tenant fixtures verify remote MySQL identity and clean up after HTTP failure', async () => {
	const fixtureRoot = await mkdtemp(join(tmpdir(), 'bixi-tenant-native-'));
	const server = createServer((_request, response) => {
		response.writeHead(503, { 'content-type': 'application/json' });
		response.end('{}');
	});
	try {
		const bin = join(fixtureRoot, 'bin');
		const log = join(fixtureRoot, 'cli.log');
		const envFile = join(fixtureRoot, 'bixi.env');
		const mysqlCa = join(fixtureRoot, 'mysql-ca.pem');
		await mkdir(bin);
		await writeFile(envFile, '');
		await writeFile(mysqlCa, 'fixture CA');
		await writeFile(join(bin, 'mysql'), `#!/usr/bin/env node
const fs = require('node:fs');
let sql = '';
process.stdin.on('data', chunk => { sql += chunk; });
process.stdin.on('end', () => fs.appendFileSync(process.env.BIXI_TEST_CLI_LOG,
	JSON.stringify({ command: 'mysql', args: process.argv.slice(2), password: process.env.MYSQL_PWD, sql }) + '\\n'));
`, { mode: 0o755 });
		await writeFile(join(bin, 'redis-cli'), `#!/usr/bin/env node
require('node:fs').appendFileSync(process.env.BIXI_TEST_CLI_LOG,
	JSON.stringify({ command: 'redis-cli', args: process.argv.slice(2), password: process.env.REDISCLI_AUTH }) + '\\n');
`, { mode: 0o755 });
		await writeFile(join(bin, 'docker'), `#!/usr/bin/env node
require('node:fs').appendFileSync(process.env.BIXI_TEST_CLI_LOG,
	JSON.stringify({ command: 'docker', args: process.argv.slice(2) }) + '\\n');
process.exitCode = 77;
`, { mode: 0o755 });
		await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
		const apiBase = `http://127.0.0.1:${server.address().port}`;
		const child = spawn(process.execPath,
			[fileURLToPath(new URL('./tenant-isolation-acceptance.mjs', import.meta.url))], {
				env: { ...process.env, PATH: `${bin}:${process.env.PATH}`, BIXI_ENV_FILE: envFile,
					BIXI_FIXTURE_DRIVER: 'native', BIXI_API_BASE_URL: apiBase, BIXI_TEST_CLI_LOG: log,
					ADMIN_USERNAME: 'admin', ADMIN_PASSWORD: 'fixture',
					OAUTH_PASSWORD_CLIENT_SECRET: 'fixture', BIXI_ENCODE_KEY: 'fixture',
					MYSQL_HOST: 'db.example.test', MYSQL_SSL_CA: mysqlCa,
					MYSQL_PORT: '3307', MYSQL_DATABASE: 'fixture_db',
					MYSQL_ROOT_USERNAME: 'fixture_root', MYSQL_ROOT_PASSWORD: 'mysql_fixture',
					REDIS_HOST: '127.0.0.1', REDIS_PORT: '6380', REDIS_DATABASE: '2',
					REDIS_PASSWORD: 'redis_fixture' },
			});
		let output = '';
		child.stdout.on('data', chunk => { output += chunk; });
		child.stderr.on('data', chunk => { output += chunk; });
		const code = await new Promise((resolve, reject) => {
			child.on('error', reject);
			child.on('close', resolve);
		});
		assert.equal(code, 1, output);
		assert.match(output, /Login failed \(503\)/);
		const calls = (await readFile(log, 'utf8')).trim().split('\n').map(JSON.parse);
		assert.deepEqual(calls.map(call => call.command), ['mysql', 'mysql', 'redis-cli']);
		for (const call of calls.filter(call => call.command === 'mysql')) {
			assert.deepEqual(call.args, ['--batch', '--silent', '--protocol=TCP',
				'--ssl-mode=VERIFY_IDENTITY', `--ssl-ca=${mysqlCa}`, '--host=db.example.test',
				'--port=3307', '--user=fixture_root', 'fixture_db']);
			assert.equal(call.password, 'mysql_fixture');
		}
		assert.match(calls[0].sql, /INSERT INTO sys_tenant/);
		assert.match(calls[1].sql, /DELETE FROM sys_tenant/);
		assert.deepEqual(calls[2].args.slice(0, -1),
			['-h', '127.0.0.1', '-p', '6380', '-n', '2', '--no-auth-warning', 'DEL']);
		assert.match(calls[2].args.at(-1), /^tenant_status::\d+$/);
		assert.equal(calls[2].password, 'redis_fixture');
	}
	finally {
		server.close();
		await rm(fixtureRoot, { recursive: true, force: true });
	}
});
