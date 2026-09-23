import assert from 'node:assert/strict';
import test from 'node:test';

import { generateStrongPassword } from './acceptance-password.mjs';

test('generateStrongPassword always satisfies the administrator password policy', () => {
	for (let attempt = 0; attempt < 100; attempt += 1) {
		const password = generateStrongPassword();
		assert.match(password, /[A-Z]/, 'password must contain an uppercase letter');
		assert.match(password, /[a-z]/, 'password must contain a lowercase letter');
		assert.match(password, /\d/, 'password must contain a digit');
		assert.match(password, /[^A-Za-z0-9]/, 'password must contain a special character');
		assert.ok(password.length >= 8, 'password must be at least eight characters');
	}
});
