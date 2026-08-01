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
	background: var(--bixi-color-paper-deep);
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 32px 28px;
	color: var(--bixi-color-text);
}

.login-shell {
	width: min(1440px, 100%);
	display: grid;
	grid-template-columns: minmax(0, 1.2fr) minmax(420px, 0.8fr);
	gap: 28px;
	align-items: stretch;
	min-height: 700px;
}

.login-hero {
	background: var(--bixi-color-paper);
	border: 1px solid var(--bixi-color-border);
	border-radius: var(--bixi-radius-lg);
	padding: 64px;
	color: var(--bixi-color-ink);
	display: flex;
	flex-direction: column;
	justify-content: space-between;
	box-shadow: var(--bixi-shadow-soft);
	min-height: 700px;
	position: relative;
	overflow: hidden;
}

.login-hero::after {
	content: '';
	position: absolute;
	right: -100px;
	top: -100px;
	width: 280px;
	height: 280px;
	border: 26px solid #f04a2a;
	border-radius: 50%;
	opacity: 0.9;
}

.brand-title {
	font-size: 48px;
	font-weight: 800;
	letter-spacing: -1px;
}

.brand-subtitle {
	margin-top: 12px;
	font-size: 22px;
	color: var(--bixi-color-muted);
}

.hero-card {
	max-width: 720px;
	margin-top: 72px;
	padding: 32px 0 0;
	border-top: 2px solid var(--bixi-color-ink);
}

.hero-title {
	font-size: 30px;
	font-weight: 800;
	letter-spacing: -0.5px;
}

.hero-desc {
	margin-top: 14px;
	font-size: 17px;
	line-height: 1.8;
	color: var(--bixi-color-muted);
}

.hero-features {
	margin-top: 26px;
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.feature-item {
	border: 1px solid var(--bixi-color-border);
	border-radius: 999px;
	padding: 9px 16px;
	font-size: 15px;
	font-weight: 600;
	white-space: nowrap;
}

.feature-item:nth-child(3n + 1) {
	color: var(--bixi-color-primary);
	border-color: #f4b2a3;
}

.feature-item:nth-child(3n + 2) {
	color: var(--bixi-color-success);
	border-color: #b7ce9f;
}

.login-panel {
	display: flex;
	align-items: center;
	justify-content: center;
}

.panel-card {
	width: 100%;
	background: var(--bixi-color-paper);
	border: 1px solid var(--bixi-color-border);
	border-radius: var(--bixi-radius-lg);
	padding: 48px 44px;
	box-shadow: var(--bixi-shadow-soft);
	min-height: 700px;
	display: flex;
	flex-direction: column;
}

.panel-header {
	margin-bottom: 22px;
}

.panel-title {
	font-size: 30px;
	font-weight: 800;
	letter-spacing: -0.5px;
}

.panel-subtitle {
	margin-top: 8px;
	color: var(--bixi-color-muted);
	font-size: 16px;
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

.panel-tabs :deep(.el-tabs__nav-wrap::after) {
	background-color: var(--bixi-color-border);
}

.panel-tabs :deep(.el-tabs__active-bar) {
	background-color: var(--bixi-color-primary);
}

.panel-tabs :deep(.el-tabs__item) {
	font-weight: 700;
	color: var(--bixi-color-muted);
}

.panel-tabs :deep(.el-tabs__item.is-active) {
	color: var(--bixi-color-primary);
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

	.login-hero,
	.panel-card {
		min-height: auto;
	}

	.login-hero {
		padding: 50px;
	}

	.hero-card {
		margin-top: 44px;
	}
}

@media (max-width: 640px) {
	.login-page {
		padding: 16px;
	}

	.login-hero,
	.panel-card {
		padding: 32px 26px;
		border-radius: var(--bixi-radius-md);
	}

	.login-hero::after {
		display: none;
	}

	.brand-title {
		font-size: 40px;
	}

	.hero-title,
	.panel-title {
		font-size: 25px;
	}

	.panel-tabs :deep(.el-tabs__content),
	.panel-tabs :deep(.el-tab-pane) {
		min-height: auto;
	}

	.hero-features {
		flex-direction: column;
		align-items: flex-start;
	}
}
</style>
