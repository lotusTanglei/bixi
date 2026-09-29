<template>
	<div class="chat-container">
		<el-container class="chat-layout">
			<el-aside width="280px" class="chat-aside">
				<SessionList @create="handleCreate" />
			</el-aside>
			<el-main class="chat-main">
				<div class="chat-content">
					<MessageList ref="messageListRef" :messages="aiStore.messageList" />
					<MessageInput
						:disabled="!aiStore.currentSession"
						:loading="aiStore.loading"
						placeholder="请输入您的问题..."
						@cancel="handleCancel"
						@send="handleSend"
					/>
				</div>
			</el-main>
		</el-container>
	</div>
</template>

<script lang="ts" name="AiChat" setup>
import { useAiStore } from '/@/stores/ai';
import { createSession, messageList, sessionList, streamChat } from '/@/api/ai/chat';
import { useMessage } from '/@/hooks/message';
import SessionList from './components/SessionList.vue';
import MessageList from './components/MessageList.vue';
import MessageInput from './components/MessageInput.vue';

const aiStore = useAiStore();
const messageListRef = ref();
const { error } = useMessage();
let streamController: AbortController | null = null;

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
	const controller = new AbortController();
	streamController = controller;
	let answer = '';

	try {
		await streamChat(
			{ sessionId: aiStore.currentSession.id, message: content },
			(chunk) => {
				answer += chunk;
				aiStore.updateMessage(assistantMessage.id, answer);
			},
			controller.signal,
		);
	} catch (err: any) {
		if (controller.signal.aborted) {
			if (!answer) aiStore.updateMessage(assistantMessage.id, '已停止生成');
		} else {
			const message = err?.msg || err?.message || '网络错误，请稍后重试';
			if (!answer) aiStore.updateMessage(assistantMessage.id, message);
			error(message);
		}
	} finally {
		if (streamController === controller) streamController = null;
		aiStore.setLoading(false);
		nextTick(() => {
			messageListRef.value?.scrollToBottom();
		});
	}
};

const handleCancel = () => streamController?.abort();

const handleCreate = async () => {
	try {
		const res = await createSession({ title: '新对话' });
		if (res.code !== 0 || !res.data) {
			error(res.msg || '创建会话失败');
			return;
		}
		aiStore.addSession(res.data);
		aiStore.clearMessages();
	} catch (err: any) {
		error(err.msg || '创建会话失败');
	}
};

const loadSessions = async () => {
	try {
		const res = await sessionList();
		if (res.code !== 0) {
			error(res.msg || '加载会话失败');
			return;
		}
		const sessions = res.data || [];
		const currentId = aiStore.currentSession?.id;
		aiStore.setSessionList(sessions);
		aiStore.setCurrentSession(sessions.find((session: any) => session.id === currentId) || sessions[0] || null);
	} catch (err: any) {
		error(err.msg || '加载会话失败');
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

onMounted(loadSessions);
onBeforeUnmount(handleCancel);
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
