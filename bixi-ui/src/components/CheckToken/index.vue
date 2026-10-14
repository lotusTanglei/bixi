<template>
	<div></div>
</template>
<script setup lang="ts" name="check-token">
import { useUserInfo } from '/@/stores/userInfo';
import { Session } from '/@/utils/storage';

const router = useRouter();
let refreshTime: ReturnType<typeof setTimeout> | undefined;
let active = true;

const scheduleRefresh = () => {
	if (!Session.getToken()) return;
	const refreshAt = Number(Session.get('refreshAt')) || 0;
	const delay = Math.min(2_147_483_647, Math.max(0, refreshAt - Date.now()));
	refreshTime = setTimeout(async () => {
		if (!active || !Session.getToken()) return;
		if (Number(Session.get('refreshAt')) > Date.now()) return scheduleRefresh();
		try {
			await useUserInfo().refreshToken();
			if (active) scheduleRefresh();
		} catch {
				if (!active) return;
				if (!Session.getToken()) router.replace('/login');
				else refreshTime = setTimeout(scheduleRefresh, 30_000);
		}
	}, delay);
};

onMounted(scheduleRefresh);
onUnmounted(() => {
	active = false;
	clearTimeout(refreshTime);
});
</script>
