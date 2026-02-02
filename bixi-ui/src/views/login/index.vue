<template>
	<div class="login-page">
		<div class="login-shell">
			<section class="login-hero">
				<div class="brand">
					<div class="brand-title">BIXI</div>
					<div class="brand-subtitle">微服务快速开发平台</div>
				</div>
				<div class="hero-card">
					<div class="hero-title">安全登录与统一授权</div>
					<div class="hero-desc">
						覆盖用户与权限、组织与角色、通知中心、任务调度、代码生成与运维监控等核心能力。
					</div>
					<div class="hero-features">
						<div class="feature-item">用户与权限</div>
						<div class="feature-item">组织与角色</div>
						<div class="feature-item">通知中心</div>
						<div class="feature-item">任务调度</div>
						<div class="feature-item">代码生成</div>
						<div class="feature-item">运维监控</div>
					</div>
				</div>
			</section>
			<section class="login-panel">
				<div class="panel-card">
					<div class="panel-header">
						<div class="panel-title">欢迎回来</div>
						<div class="panel-subtitle">请使用你的账号继续</div>
					</div>
					<el-tabs v-model="tabsActiveName" class="panel-tabs">
						<el-tab-pane :label="$t('label.one1')" name="account">
							<Password @signInSuccess="signInSuccess" />
						</el-tab-pane>
						<el-tab-pane :label="$t('label.two2')" name="mobile">
							<Mobile @signInSuccess="signInSuccess" />
						</el-tab-pane>
						<el-tab-pane :label="$t('label.register')" name="register" v-if="registerEnable">
							<Register @afterSuccess="tabsActiveName = 'account'" />
						</el-tab-pane>
					</el-tabs>
				</div>
			</section>
		</div>
	</div>
</template>

<script setup lang="ts" name="loginIndex">
import { useThemeConfig } from '/@/stores/themeConfig';
import { NextLoading } from '/@/utils/loading';
import { useI18n } from 'vue-i18n';
import { formatAxis } from '/@/utils/formatTime';
import { useMessage } from '/@/hooks/message';
import { Session } from '/@/utils/storage';
import { initBackEndControlRoutes } from '/@/router/backEnd';

const Password = defineAsyncComponent(() => import('./component/password.vue'));
const Mobile = defineAsyncComponent(() => import('./component/mobile.vue'));
const Register = defineAsyncComponent(() => import('./component/register.vue'));

const storesThemeConfig = useThemeConfig();
const { themeConfig } = storeToRefs(storesThemeConfig);
const { t } = useI18n();
const route = useRoute();
const router = useRouter();

const registerEnable = ref(import.meta.env.VITE_REGISTER_ENABLE === 'true');

const tabsActiveName = ref('account');

const getThemeConfig = computed(() => {
	return themeConfig.value;
});

const signInSuccess = async () => {
	const isNoPower = await initBackEndControlRoutes();
	if (isNoPower) {
		useMessage().wraning('抱歉，您没有登录权限');
		Session.clear();
	} else {
		let currentTimeInfo = formatAxis(new Date());
		if (route.query?.redirect) {
			router.push({
				path: <string>route.query?.redirect,
				query: Object.keys(<string>route.query?.params).length > 0 ? JSON.parse(<string>route.query?.params) : '',
			});
		} else {
			router.push('/');
		}
		const signInText = t('signInText');
		useMessage().success(`${currentTimeInfo}，${signInText}`);
		NextLoading.start();
	}
};

onMounted(() => {
	NextLoading.done();
});
</script>
<style scoped>
.login-page {
	min-height: 100vh;
	background: radial-gradient(circle at top, #f5f7ff 0%, #eef2ff 32%, #e8f0ff 100%);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 40px 24px;
	color: #0f1c3f;
}

.login-shell {
	width: min(1600px, 100%);
	display: grid;
	grid-template-columns: minmax(0, 1.45fr) minmax(0, 0.85fr);
	gap: 46px;
	align-items: stretch;
	min-height: 760px;
}

.login-hero {
	background: linear-gradient(140deg, #2f5bff 0%, #6c8cff 55%, #9eb6ff 100%);
	border-radius: 38px;
	padding: 70px;
	color: #ffffff;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	box-shadow: 0 30px 80px rgba(46, 85, 255, 0.25);
	min-height: 760px;
}

.brand-title {
	font-size: 40px;
	font-weight: 700;
	letter-spacing: 0.5px;
}

.brand-subtitle {
	margin-top: 12px;
	font-size: 20px;
	opacity: 0.85;
}

.hero-card {
	margin-top: 84px;
	background: rgba(255, 255, 255, 0.15);
	border-radius: 20px;
	padding: 40px;
	backdrop-filter: blur(10px);
}

.hero-title {
	font-size: 26px;
	font-weight: 600;
}

.hero-desc {
	margin-top: 12px;
	font-size: 18px;
	line-height: 1.6;
	opacity: 0.85;
}

.hero-features {
	margin-top: 24px;
	display: flex;
	flex-wrap: wrap;
	gap: 12px;
}

.feature-item {
	background: rgba(255, 255, 255, 0.18);
	border-radius: 999px;
	padding: 12px 20px;
	font-size: 18px;
	letter-spacing: 0.2px;
	white-space: nowrap;
}

.login-panel {
	display: flex;
	align-items: center;
	justify-content: center;
}

.panel-card {
	width: 100%;
	background: #ffffff;
	border-radius: 34px;
	padding: 52px 46px;
	box-shadow: 0 20px 60px rgba(14, 30, 64, 0.12);
	min-height: 760px;
	display: flex;
	flex-direction: column;
}

.panel-header {
	margin-bottom: 24px;
}

.panel-title {
	font-size: 28px;
	font-weight: 600;
}

.panel-subtitle {
	margin-top: 8px;
	color: #6f7a91;
	font-size: 18px;
}

.panel-tabs {
	flex: 1;
	display: flex;
	flex-direction: column;
}

.panel-tabs :deep(.el-tabs__header) {
	margin-bottom: 18px;
	order: 0;
}

.panel-tabs :deep(.el-tabs__item) {
	font-weight: 500;
}

.panel-tabs :deep(.el-tabs__content) {
	flex: 1;
	order: 1;
	min-height: 360px;
}

.panel-tabs :deep(.el-tab-pane) {
	height: 100%;
	min-height: 360px;
}

@media (max-width: 960px) {
	.login-shell {
		grid-template-columns: 1fr;
		min-height: auto;
	}

	.login-hero {
		padding: 50px;
		min-height: auto;
	}

	.hero-card {
		margin-top: 44px;
	}
}

@media (max-width: 640px) {
	.login-page {
		padding: 20px 16px;
	}

	.login-hero {
		padding: 36px;
		border-radius: 24px;
	}

	.panel-card {
		padding: 36px 28px;
		border-radius: 24px;
		min-height: auto;
	}

	.panel-tabs :deep(.el-tabs__content) {
		min-height: auto;
	}

	.panel-tabs :deep(.el-tab-pane) {
		min-height: auto;
	}


	.hero-features {
		flex-direction: column;
	}
}
</style>
