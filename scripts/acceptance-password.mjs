import { randomBytes } from 'node:crypto';

export function generateStrongPassword() {
	return `Aa1!${randomBytes(18).toString('base64url')}`;
}
