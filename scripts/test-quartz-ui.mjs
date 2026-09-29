#!/usr/bin/env node
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const jobView = readFileSync(new URL('../bixi-ui/src/views/job/job-manage/index.vue', import.meta.url), 'utf8');
const recordView = readFileSync(new URL('../bixi-ui/src/views/job/job-manage/job-record.vue', import.meta.url), 'utf8');
const jobForm = readFileSync(new URL('../bixi-ui/src/views/job/job-manage/form.vue', import.meta.url), 'utf8');
const jobApi = readFileSync(new URL('../bixi-ui/src/api/job/job.ts', import.meta.url), 'utf8');
const recordApi = readFileSync(new URL('../bixi-ui/src/api/job/job-record.ts', import.meta.url), 'utf8');

test('job actions use the current status and id fields', () => {
	assert.doesNotMatch(jobView, /row\.jobStatus|row\.jobId/);
	assert.match(jobView, /v-if="scope\.row\.status !== '2'"/);
	assert.match(jobView, /const jobStatus = row\.status/);
	assert.match(jobView, /formDialogRef\.value\.openDialog\(row\.id\)/);
	assert.match(jobView, /jobLogRef\.value\.openDialog\(row\.id\)/);
});

test('job buttons expose the same permissions enforced by backend endpoints', () => {
	assert.match(jobView, /:export="'job_sys_job_export'"/);
	assert.match(jobView, /v-auth="'job_sys_job_record_view'"[^>]*@click="handleJobLog\(scope\.row\)"/s);
	assert.match(jobView, /v-auth="'job_sys_job_start_job'"[^>]*@click="handleStartJob\(scope\.row\)"/s);
	assert.match(jobView, /v-auth="'job_sys_job_shutdown_job'"[^>]*@click="handleShutDownJob\(scope\.row\)"/s);
	assert.match(jobView, /v-auth="'job_sys_job_run_job'"[^>]*@click="handleRunJob\(scope\.row\)"/s);
});

test('job record dialog only offers operations backed by its controller', () => {
	assert.match(recordView, /v-auth="'job_sys_job_record_del'"/);
	assert.doesNotMatch(recordView, /v-auth="'sys_log_del'"|v-auth="'pix_log_edit'"/);
	assert.doesNotMatch(recordView, /formDialogRef\.openDialog|const formDialogRef/);
});

test('job form configures bounded retries and submits a writable-field whitelist', () => {
	assert.match(jobForm, /:title="form\.id \?/);
	assert.doesNotMatch(jobForm, /form\.jobId|jobStatus|jobExecuteStatus/);
	assert.match(jobForm, /v-model="form\.retryCount"[^>]*:min="0"[^>]*:max="5"/s);
	assert.match(jobForm, /v-model="form\.retryIntervalSeconds"[^>]*:min="1"[^>]*:max="300"/s);
	assert.doesNotMatch(jobForm, /addObj\(form\)|putObj\(form\)/);
	assert.match(jobForm, /const payload = \{[\s\S]*retryCount: form\.retryCount,[\s\S]*retryIntervalSeconds: form\.retryIntervalSeconds,/);
});

test('job history exposes retry and node-recovery evidence', () => {
	assert.match(recordView, /prop="executionId"/);
	assert.match(recordView, /scope\.row\.attempt[^\n]+scope\.row\.maxAttempts/);
	assert.match(recordView, /prop="triggerType"/);
	assert.match(recordView, /scope\.row\.recovered/);
});

test('Quartz API wrappers target the protected job and record endpoints', () => {
	assert.match(jobApi, /url:\s*'\/job\/sys-job\/start-job\/' \+ jobId/);
	assert.match(jobApi, /url:\s*'\/job\/sys-job\/run-job\/' \+ jobId/);
	assert.match(jobApi, /url:\s*'\/job\/sys-job\/shutdown-job\/' \+ jobId/);
	assert.match(recordApi, /url:\s*'\/job\/sys-job-record\/page'/);
	assert.match(recordApi, /url:\s*'\/job\/sys-job-record'/);
});
