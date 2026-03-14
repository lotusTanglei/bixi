<template>
	<div class="chat-container">
		<el-container class="chat-layout">
			<el-aside width="280px" class="chat-aside">
				<SessionList />
			</el-aside>
			<el-main class="chat-main">
				<div class="chat-content">
					<MessageList ref="messageListRef" :messages="aiStore.messageList" />
					<MessageInput
						:disabled="!aiStore.currentSession"
						:loading="aiStore.loading"
						placeholder="请输入您的问题..."
						@send="handleSend"
					/>
				</div>
			</el-main>
		</el-container>
	</div>
</template>

<script lang="ts" name="AiChat" setup>
import { useAiStore } from '/@/stores/ai';
import { chat, ragChat, messageList } from '/@/api/ai/chat';
import { useMessage } from '/@/hooks/message';
import SessionList from './components/SessionList.vue';
import MessageList from './components/MessageList.vue';
import MessageInput from './components/MessageInput.vue';

const aiStore = useAiStore();
const messageListRef = ref();
const { success, error } = useMessage();

const generateId = () => {
	return Date.now().toString(36) + Math.random().toString(36).substr(2);
};

const handleSend = async (content: string) => {
	if (!aiStore.currentSession || aiStore.loading) return;

	const userMessage = {
		id: generateId(),
		sessionId: aiStore.currentSession.id,
		role: 'user' as const,
		content,
		createTime: new Date().toISOString(),
	};

	aiStore.addMessage(userMessage);
	aiStore.setLoading(true);

	const assistantMessage = {
		id: generateId(),
		sessionId: aiStore.currentSession.id,
		role: 'assistant' as const,
		content: '',
		createTime: new Date().toISOString(),
	};
	aiStore.addMessage(assistantMessage);

	try {
		const res = await chat({
			sessionId: aiStore.currentSession.id,
			content,
			...aiStore.config,
		});

		if (res.code === 0) {
			aiStore.updateMessage(assistantMessage.id, res.data?.content || res.msg || '回复成功');
		} else {
			aiStore.updateMessage(assistantMessage.id, res.msg || '请求失败');
			error(res.msg || '请求失败');
		}
	} catch (err: any) {
		aiStore.updateMessage(assistantMessage.id, '网络错误，请稍后重试');
		error(err.msg || '网络错误');
	} finally {
		aiStore.setLoading(false);
		nextTick(() => {
			messageListRef.value?.scrollToBottom();
		});
	}
};

const loadMessages = async (sessionId: string) => {
	try {
		const res = await messageList(sessionId);
		if (res.code === 0) {
			aiStore.setMessageList(res.data || []);
		}
	} catch (err: any) {
		error(err.msg || '加载消息失败');
	}
};

watch(
	() => aiStore.currentSession,
	(newSession) => {
		if (newSession) {
			loadMessages(newSession.id);
		} else {
			aiStore.clearMessages();
		}
	},
	{ immediate: true }
);
</script>

<style lang="scss" scoped>
.chat-container {
	height: 100%;
	background-color: #f5f7fa;
}

.chat-layout {
	height: 100%;
}

.chat-aside {
	background-color: #fff;
	border-right: 1px solid #e4e7ed;
	overflow: hidden;
}

.chat-main {
	padding: 0;
	overflow: hidden;
}

.chat-content {
	display: flex;
	flex-direction: column;
	height: 100%;
}
</style>
