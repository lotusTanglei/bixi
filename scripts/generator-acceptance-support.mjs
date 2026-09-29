import { spawn } from 'node:child_process';
import process from 'node:process';

const DEFAULT_GENERATOR_FUNCTION_NAMES = new Set(['publicParam', 'dictAggregate']);

/**
 * Convert a database table name into the stable URL/function token used by generated code.
 * The historical default keeps its published name; custom names are derived from the table.
 */
export function functionNameForTable(tableName, options = {}) {
	const value = String(tableName ?? '').trim();
	if (!value) throw new TypeError('table name must not be empty');
	const reservedNames = new Set(DEFAULT_GENERATOR_FUNCTION_NAMES);
	if (options.reservedNames !== undefined) {
		if (!Array.isArray(options.reservedNames)) throw new TypeError('reservedNames must be an array');
		for (const name of options.reservedNames) reservedNames.add(String(name));
	}
	if (value.toLowerCase() === 'sys_public_param') return 'publicParam';

	const parts = value.split(/[^A-Za-z0-9]+/).filter(Boolean);
	let candidate = parts.map((part, index) => {
		const lower = part.toLowerCase();
		return index === 0 ? lower : `${lower[0].toUpperCase()}${lower.slice(1)}`;
	}).join('');
	if (!candidate) candidate = 'table';
	if (!/^[A-Za-z_$]/.test(candidate)) candidate = `table${candidate}`;

	let suffix = '';
	let result = candidate;
	while (reservedNames.has(result)) {
		suffix = suffix ? `${suffix}2` : 'Table';
		result = `${candidate}${suffix}`;
	}
	return result;
}

export function authTokenPathForMode(mode) {
	if (mode === 'single') return '/admin/oauth2/token';
	if (mode === 'cloud') return '/auth/oauth2/token';
	throw new TypeError(`unsupported deployment mode: ${mode}`);
}

export function healthChecksForMode(mode) {
	if (mode === 'single') {
		return [{ name: 'single', path: '/admin/actuator/health' }];
	}
	if (mode === 'cloud') {
		return [
			{ name: 'backend', path: '/actuator/health' },
			{ name: 'Generator', path: '/gen/actuator/health' },
			{ name: 'UPMS', path: '/admin/actuator/health' },
			{ name: 'Auth', path: '/auth/actuator/health' },
		];
	}
	throw new TypeError(`unsupported deployment mode: ${mode}`);
}

export function matchesGeneratorAudit(record, expectation) {
	if (String(record?.type) !== '0'
		|| record?.title !== '生成代码到项目目录'
		|| record?.method !== 'POST'
		|| record?.requestUri !== expectation?.requestUri) return false;
	const tableId = String(expectation?.tableId || '');
	const templateVersion = String(expectation?.templateVersion || '');
	if (!/^\d+$/.test(tableId) || templateVersion.length === 0 || typeof record.params !== 'string') return false;
	let parameters;
	try {
		parameters = JSON.parse(record.params);
	} catch {
		return false;
	}
	if (!Array.isArray(parameters) || parameters.length !== 1 || !parameters[0]
		|| typeof parameters[0] !== 'object' || Array.isArray(parameters[0])) return false;
	const request = parameters[0];
	if (request.templateVersion !== templateVersion || !Array.isArray(request.tableIds)
		|| request.tableIds.length !== 1) return false;
	if (String(request.tableIds[0]) === tableId) return true;
	const escapedId = tableId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
	return new RegExp(`"tableIds"\\s*:\\s*\\[\\s*(?:"${escapedId}"|${escapedId})\\s*\\]`).test(record.params);
}

export function matchesOperationAudit(record, expectation) {
	const marker = String(expectation?.marker || '');
	return marker.length > 0
		&& String(record?.type) === '0'
		&& record?.title === expectation?.title
		&& record?.method === expectation?.method
		&& record?.requestUri === expectation?.requestUri
		&& typeof record?.params === 'string'
		&& record.params.includes(marker);
}

export async function readResponseBodyLimited(response, maximumBytes) {
	if (!Number.isSafeInteger(maximumBytes) || maximumBytes < 1) {
		throw new RangeError('maximumBytes must be a positive safe integer');
	}
	const declared = response.headers.get('content-length');
	if (declared && /^\d+$/.test(declared) && BigInt(declared) > BigInt(maximumBytes)) {
		throw bodyLimitError(maximumBytes);
	}
	if (!response.body) return Buffer.alloc(0);
	const reader = response.body.getReader();
	const chunks = [];
	let totalBytes = 0;
	while (true) {
		const { done, value } = await reader.read();
		if (done) break;
		totalBytes += value.byteLength;
		if (totalBytes > maximumBytes) {
			await reader.cancel('binary response exceeded acceptance limit');
			throw bodyLimitError(maximumBytes);
		}
		chunks.push(Buffer.from(value.buffer, value.byteOffset, value.byteLength));
	}
	return Buffer.concat(chunks, totalBytes);
}

export function runBoundedProcess(command, args, options) {
	const maxStdoutBytes = positiveInteger(options?.maxStdoutBytes, 'maxStdoutBytes');
	const maxStderrBytes = positiveInteger(options?.maxStderrBytes, 'maxStderrBytes');
	const timeoutMillis = positiveInteger(options?.timeoutMillis, 'timeoutMillis');
	const killGraceMillis = positiveInteger(options?.killGraceMillis ?? 1_000, 'killGraceMillis');
	return new Promise((resolvePromise, rejectPromise) => {
		const ownsProcessGroup = process.platform !== 'win32';
		const child = spawn(command, args, {
			cwd: options?.cwd,
			env: options?.env,
			stdio: ['ignore', 'pipe', 'pipe'],
			detached: ownsProcessGroup,
		});
		const stdout = [];
		const stderr = [];
		let stdoutBytes = 0;
		let stderrBytes = 0;
		let failureCode;
		let failureMessage;
		let settled = false;
		let killTimer;

		const signalTree = signal => {
			if (ownsProcessGroup && child.pid) {
				try {
					process.kill(-child.pid, signal);
					return;
				} catch (error) {
					if (error.code !== 'ESRCH' && error.code !== 'EPERM') throw error;
				}
			}
			if (child.exitCode === null) child.kill(signal);
		};
		const stop = (code, message) => {
			if (failureCode || settled) return;
			failureCode = code;
			failureMessage = message;
			try {
				signalTree('SIGTERM');
				killTimer = setTimeout(() => {
					try { signalTree('SIGKILL'); } catch { /* close/error reports the original failure */ }
				}, killGraceMillis);
			} catch (error) {
				settled = true;
				clearTimeout(timeout);
				rejectPromise(error);
			}
		};
		const append = (chunks, chunk, usedBytes, maximumBytes, code, streamName) => {
			const remaining = Math.max(0, maximumBytes - usedBytes);
			if (remaining > 0) chunks.push(chunk.subarray(0, remaining));
			if (chunk.length > remaining) stop(code, `${command} ${streamName} exceeded ${maximumBytes} bytes`);
			return usedBytes + Math.min(chunk.length, remaining);
		};
		const timeout = setTimeout(() => stop('PROCESS_TIMEOUT', `${command} timed out after ${timeoutMillis}ms`), timeoutMillis);
		child.stdout.on('data', chunk => {
			stdoutBytes = append(stdout, chunk, stdoutBytes, maxStdoutBytes, 'PROCESS_STDOUT_LIMIT', 'stdout');
		});
		child.stderr.on('data', chunk => {
			stderrBytes = append(stderr, chunk, stderrBytes, maxStderrBytes, 'PROCESS_STDERR_LIMIT', 'stderr');
		});
		child.once('error', error => {
			if (settled) return;
			settled = true;
			clearTimeout(timeout);
			clearTimeout(killTimer);
			rejectPromise(error);
		});
		child.once('close', (exitCode, signal) => {
			if (settled) return;
			settled = true;
			clearTimeout(timeout);
			clearTimeout(killTimer);
			const result = {
				exitCode,
				signal,
				stdout: Buffer.concat(stdout, stdoutBytes).toString('utf8'),
				stderr: Buffer.concat(stderr, stderrBytes).toString('utf8'),
			};
			if (failureCode) {
				const error = new Error(failureMessage);
				error.code = failureCode;
				error.result = result;
				rejectPromise(error);
				return;
			}
			if (exitCode !== 0) {
				const error = new Error(`${command} failed (${signal || exitCode}): ${result.stderr.trim()}`);
				error.code = 'PROCESS_EXIT';
				error.result = result;
				rejectPromise(error);
				return;
			}
			resolvePromise(Buffer.concat(stdout, stdoutBytes));
		});
	});
}

export function safeArtifactFilename(label, tableName, extension) {
	if (!/^[A-Za-z0-9_-]+$/.test(String(label)) || !/^[A-Za-z0-9]+$/.test(String(extension))) {
		throw new TypeError('artifact label and extension must be safe filename tokens');
	}
	const value = String(tableName || '');
	if (!value) throw new TypeError('table name must not be empty');
	return `${label}-${Buffer.from(value, 'utf8').toString('base64url')}.${extension}`;
}

function bodyLimitError(maximumBytes) {
	const error = new Error(`binary response exceeded ${maximumBytes} bytes`);
	error.code = 'BINARY_BODY_LIMIT';
	return error;
}

function positiveInteger(value, name) {
	if (!Number.isSafeInteger(value) || value < 1) throw new RangeError(`${name} must be a positive safe integer`);
	return value;
}
