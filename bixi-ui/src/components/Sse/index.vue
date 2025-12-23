<template>
	<div></div>
</template>

<script setup lang="ts" name="global-sse">
import { Session } from '/@/utils/storage';
import other from '/@/utils/other';

const emit = defineEmits(['message', 'error', 'open']);

const props = defineProps({
	uri: {
		type: String,
		required: true,
	},
	reconnectInterval: {
		type: Number,
		default: 3000,
	},
});

const state = reactive({
	source: null as EventSource | null,
	reconnectTimer: null as number | null,
	closed: false,
});

const buildUrl = () => {
	const host = window.location.host;
	const baseURL = import.meta.env.VITE_API_URL;
	const token = Session.getToken();
	const tenant = Session.getTenant?.() ?? '';
	const protocol = window.location.protocol === 'https:' ? 'https' : 'http';
	const path = other.adaptationUrl(props.uri);
	const query = new URLSearchParams();
	if (token) query.set('access_token', token);
	if (tenant) query.set('TENANT-ID', tenant);
	return `${protocol}://${host}${baseURL}${path}?${query.toString()}`;
};

const cleanup = () => {
	if (state.source) {
		state.source.close();
		state.source = null;
	}
	if (state.reconnectTimer) {
		window.clearTimeout(state.reconnectTimer);
		state.reconnectTimer = null;
	}
};

const connect = () => {
	if (state.closed) return;
	cleanup();
	try {
		state.source = new EventSource(buildUrl());
		state.source.onopen = () => emit('open');
		state.source.onmessage = (event) => emit('message', event.data);
		state.source.onerror = () => {
			emit('error');
			if (state.closed) return;
			if (!state.reconnectTimer) {
				state.reconnectTimer = window.setTimeout(() => {
					state.reconnectTimer = null;
					connect();
				}, props.reconnectInterval);
			}
		};
	} catch {
		emit('error');
	}
};

onMounted(connect);

onUnmounted(() => {
	state.closed = true;
	cleanup();
});
</script>

