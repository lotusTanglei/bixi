#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(fileURLToPath(new URL('..', import.meta.url)));
const read = (relative) => readFileSync(join(root, relative), 'utf8');

const noticeApi = read('bixi-ui/src/api/admin/notice.ts');
const recordDialog = read('bixi-ui/src/views/admin/notice/record-dialog.vue');
const noticeIndex = read('bixi-ui/src/views/admin/notice/index.vue');

assert.match(noticeApi, /export const retryDelivery[\s\S]*delivery\/retry/,
    'notice API must expose the recipient delivery retry endpoint');
assert.match(recordDialog, /deliveryStatus/, 'record dialog must filter delivery status');
for (const field of [
    'deliveryStatus',
    'deliveryAttempts',
    'deliveryLastError',
    'deliveryLastAttemptAt',
    'deliveryDeliveredAt',
]) {
    assert.match(recordDialog, new RegExp(field), `record dialog must display ${field}`);
}
assert.match(noticeIndex, /retryDelivery/, 'notice management must invoke delivery retry');
assert.match(noticeIndex, /sys_notice_send/, 'delivery retry must use the send permission');

console.log('notice UI contract: 1/1');
