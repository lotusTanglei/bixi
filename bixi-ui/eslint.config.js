import { fileURLToPath } from 'node:url';
import path from 'node:path';
import js from '@eslint/js';
import { FlatCompat } from '@eslint/eslintrc';
import legacyConfig from './.eslintrc.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const compat = new FlatCompat({
	baseDirectory: __dirname,
	recommendedConfig: js.configs.recommended,
	allConfig: js.configs.all,
});

export default [
	{
		ignores: [
			'*.sh',
			'node_modules',
			'lib',
			'*.md',
			'*.scss',
			'*.woff',
			'*.ttf',
			'.vscode',
			'.idea',
			'dist',
			'mock',
			'public',
			'bin',
			'build',
			'config',
			'index.html',
			'src/assets',
		],
	},
	...compat.config(legacyConfig),
];
