import { randomBytes } from 'node:crypto';
import { execFile as execFileCallback } from 'node:child_process';
import { unlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { promisify } from 'node:util';

const execFile = promisify(execFileCallback);
const MAX_XML_BYTES = 16 * 1024 * 1024;

/**
 * Return the XML payloads from an XLSX response for black-box scope checks.
 * The export endpoint returns an OOXML ZIP, so parsing it as JSON loses the
 * actual rows and turns a successful response into a false failure.
 */
export async function extractWorkbookText(body) {
	const bytes = Buffer.from(body || []);
	if (bytes.length < 4 || bytes[0] !== 0x50 || bytes[1] !== 0x4b) {
		throw new Error('user export is not an XLSX archive');
	}

	const archive = join(tmpdir(), `bixi-tenant-export-${randomBytes(8).toString('hex')}.xlsx`);
	await writeFile(archive, bytes, { mode: 0o600 });
	try {
		const { stdout: listing } = await execFile('unzip', ['-Z1', archive], {
			maxBuffer: 1024 * 1024,
		});
		const entries = listing.split(/\r?\n/)
			.filter(entry => entry.startsWith('xl/') && entry.endsWith('.xml'));
		if (entries.length === 0) {
			throw new Error('user export has no XLSX XML entries');
		}

		const xml = [];
		for (const entry of entries.slice(0, 64)) {
			const result = await execFile('unzip', ['-p', archive, entry], {
				maxBuffer: MAX_XML_BYTES,
				encoding: 'utf8',
			});
			xml.push(result.stdout);
		}
		return xml.join('\n');
	}
	finally {
		await unlink(archive).catch(() => undefined);
	}
}
