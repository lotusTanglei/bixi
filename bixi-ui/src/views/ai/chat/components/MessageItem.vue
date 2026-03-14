<template>
	<div class="message-item" :class="{ 'message-user': message.role === 'user', 'message-assistant': message.role === 'assistant' }">
		<div class="message-avatar">
			<el-avatar v-if="message.role === 'user'" :size="36" icon="UserFilled" />
			<el-avatar v-else :size="36" icon="Monitor" style="background-color: #409eff" />
		</div>
		<div class="message-content">
			<div class="message-header">
				<span class="message-role">{{ message.role === 'user' ? '我' : 'AI' }}</span>
				<span class="message-time">{{ formatTime(message.createTime) }}</span>
			</div>
			<div class="message-text">
				<el-scrollbar max-height="400px">
					<div class="message-text-content" v-html="formatContent(message.content)"></div>
				</el-scrollbar>
			</div>
			<div class="message-actions">
				<el-button link size="small" @click="handleCopy">
					<el-icon><CopyDocument /></el-icon>
					复制
				</el-button>
			</div>
		</div>
	</div>
</template>

<script lang="ts" name="MessageItem" setup>
import { CopyDocument } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';

interface Message {
	id: string;
	sessionId: string;
	role: 'user' | 'assistant';
	content: string;
	createTime: string;
}

const props = defineProps<{
	message: Message;
}>();

const formatTime = (time: string) => {
	if (!time) return '';
	const date = new Date(time);
	const now = new Date();
	const isToday = date.toDateString() === now.toDateString();
	if (isToday) {
		return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
	}
	return date.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' });
};

const formatContent = (content: string) => {
	if (!content) return '';
	return content.replace(/\n/g, '<br>');
};

const handleCopy = async () => {
	try {
		await navigator.clipboard.writeText(props.message.content);
		ElMessage.success('复制成功');
	} catch {
		ElMessage.error('复制失败');
	}
};
</script>

<style lang="scss" scoped>
.message-item {
	display: flex;
	gap: 12px;
	margin-bottom: 20px;

	&.message-user {
		flex-direction: row-reverse;

		.message-content {
			align-items: flex-end;
		}

		.message-text {
			background-color: #409eff;
			color: #fff;
		}

		.message-header {
			flex-direction: row-reverse;
		}

		.message-actions {
			justify-content: flex-end;
		}
	}

	&.message-assistant {
		.message-text {
			background-color: #f4f4f5;
			color: #303133;
		}
	}
}

.message-avatar {
	flex-shrink: 0;
}

.message-content {
	display: flex;
	flex-direction: column;
	gap: 6px;
	max-width: 70%;
}

.message-header {
	display: flex;
	align-items: center;
	gap: 8px;
}

.message-role {
	font-size: 14px;
	font-weight: 500;
	color: #606266;
}

.message-time {
	font-size: 12px;
	color: #909399;
}

.message-text {
	padding: 12px 16px;
	border-radius: 8px;
	max-width: 100%;
	word-break: break-word;
}

.message-text-content {
	line-height: 1.6;
}

.message-actions {
	display: flex;
	gap: 8px;
	opacity: 0;
	transition: opacity 0.2s;
}

.message-item:hover .message-actions {
	opacity: 1;
}
</style>
