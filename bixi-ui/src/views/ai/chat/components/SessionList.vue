<template>
	<div class="session-list-container">
		<div class="session-header">
			<el-button type="primary" icon="Plus" class="new-chat-btn" @click="handleCreate">新建对话</el-button>
		</div>
		<el-scrollbar class="session-scrollbar">
			<el-menu :default-active="currentSessionId" class="session-menu" @select="handleSelect">
				<session-item
					v-for="session in sessionList"
					:key="session.id"
					:session="session"
					@delete="handleDelete"
					@rename="handleRename"
					@select="handleSelectSession"
				/>
			</el-menu>
			<el-empty v-if="sessionList.length === 0" description="暂无会话" :image-size="80" />
		</el-scrollbar>
	</div>
</template>

<script lang="ts" name="SessionList" setup>
import { computed } from 'vue';
import { useAiStore } from '/@/stores/ai';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';
import SessionItem from './SessionItem.vue';

const { t } = useI18n();
const aiStore = useAiStore();

const emit = defineEmits<{
	(e: 'select', session: any): void;
	(e: 'create'): void;
}>();

const sessionList = computed(() => aiStore.sessionList);

const currentSessionId = computed(() => aiStore.currentSession?.id || '');

const handleCreate = () => {
	emit('create');
};

const handleSelect = (index: string) => {
	const session = sessionList.value.find((s) => s.id === index);
	if (session) {
		aiStore.setCurrentSession(session);
		emit('select', session);
	}
};

const handleSelectSession = (session: any) => {
	aiStore.setCurrentSession(session);
	emit('select', session);
};

const handleDelete = async (id: string) => {
	try {
		await useMessageBox().confirm('确定要删除该会话吗？');
		aiStore.removeSession(id);
		useMessage().success('删除成功');
	} catch {
		// 用户取消
	}
};

const handleRename = (id: string) => {
	useMessageBox()
		.prompt('请输入新的会话标题', '重命名', {
			confirmButtonText: '确定',
			cancelButtonText: '取消',
			inputPattern: /^.{1,50}$/,
			inputErrorMessage: '标题长度为1-50个字符',
		})
		.then(({ value }) => {
			aiStore.updateSessionTitle(id, value);
			useMessage().success('重命名成功');
		})
		.catch(() => {
			// 用户取消
		});
};
</script>

<style lang="scss" scoped>
.session-list-container {
	display: flex;
	flex-direction: column;
	height: 100%;
	background-color: var(--el-bg-color);
}

.session-header {
	padding: 16px;
	border-bottom: 1px solid var(--el-border-color-lighter);
}

.new-chat-btn {
	width: 100%;
}

.session-scrollbar {
	flex: 1;
	overflow: hidden;
}

.session-menu {
	border-right: none;
	background-color: transparent;

	:deep(.el-menu-item) {
		height: auto;
		line-height: normal;
		padding: 12px 16px;
		margin: 4px 8px;
		border-radius: 8px;

		&:hover {
			background-color: var(--el-fill-color-light);
		}

		&.is-active {
			background-color: var(--el-color-primary-light-9);
			color: var(--el-color-primary);
		}
	}
}
</style>
