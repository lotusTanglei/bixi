<template>
	<el-menu-item :index="session.id" @click="handleSelect">
		<div class="session-item">
			<div class="session-content">
				<div class="session-title">{{ session.title }}</div>
				<div class="session-time">{{ formatTime(session.createTime) }}</div>
			</div>
			<div class="session-actions" v-show="isHovered" @click.stop>
				<el-button icon="Edit" text size="small" @click="handleRename" />
				<el-button icon="Delete" text size="small" @click="handleDelete" />
			</div>
		</div>
	</el-menu-item>
</template>

<script lang="ts" name="SessionItem" setup>
import { ref } from 'vue';

interface Session {
	id: string;
	title: string;
	createTime: string;
}

const props = defineProps<{
	session: Session;
}>();

const emit = defineEmits<{
	(e: 'delete', id: string): void;
	(e: 'rename', id: string): void;
	(e: 'select', session: Session): void;
}>();

const isHovered = ref(false);

const formatTime = (time: string) => {
	const date = new Date(time);
	const now = new Date();
	const diff = now.getTime() - date.getTime();
	const days = Math.floor(diff / (1000 * 60 * 60 * 24));

	if (days === 0) {
		return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
	} else if (days === 1) {
		return '昨天';
	} else if (days < 7) {
		return `${days}天前`;
	} else {
		return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
	}
};

const handleSelect = () => {
	emit('select', props.session);
};

const handleDelete = () => {
	emit('delete', props.session.id);
};

const handleRename = () => {
	emit('rename', props.session.id);
};

defineExpose({
	isHovered,
});
</script>

<style lang="scss" scoped>
.session-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	width: 100%;
	padding-right: 8px;

	&:hover .session-actions {
		opacity: 1;
	}
}

.session-content {
	flex: 1;
	overflow: hidden;
	min-width: 0;
}

.session-title {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	font-size: 14px;
}

.session-time {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	margin-top: 2px;
}

.session-actions {
	display: flex;
	gap: 4px;
	opacity: 0;
	transition: opacity 0.2s;
}
</style>
