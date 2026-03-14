<template>
	<div class="knowledge-container">
		<el-container class="knowledge-layout">
			<el-aside width="280px" class="knowledge-aside">
				<div class="aside-header">
					<span class="title">知识库对话</span>
					<el-switch v-model="ragMode" active-text="RAG" inactive-text="普通" />
				</div>
				<div class="document-selector">
					<el-select
						v-model="selectedDocuments"
						multiple
						collapse-tags
						collapse-tags-tooltip
						placeholder="选择知识库文档"
						class="doc-select"
					>
						<el-option
							v-for="doc in documentList"
							:key="doc.id"
							:label="doc.name"
							:value="doc.id"
						/>
					</el-select>
				</div>
			</el-aside>
			<el-main class="knowledge-main">
				<div class="knowledge-content">
					<div class="message-area" ref="messageAreaRef">
						<div
							v-for="msg in messageList"
							:key="msg.id"
							:class="['message-item', msg.role]"
						>
							<div class="message-avatar">
								<el-avatar :size="32" :icon="msg.role === 'user' ? User : ChatDotRound" />
							</div>
							<div class="message-body">
								<div class="message-content" v-html="formatContent(msg.content)"></div>
								<SourceReference
									v-if="msg.role === 'assistant' && msg.sources?.length"
									:sources="msg.sources"
								/>
							</div>
						</div>
						<div v-if="loading" class="message-item assistant">
							<div class="message-avatar">
								<el-avatar :size="32" :icon="ChatDotRound" />
							</div>
							<div class="message-body">
								<div class="message-loading">
									<el-icon class="is-loading"><Loading /></el-icon>
									<span>正在思考中...</span>
								</div>
							</div>
						</div>
					</div>
					<div class="input-area">
						<el-input
							v-model="inputContent"
							type="textarea"
							:rows="3"
							:placeholder="ragMode ? '基于知识库提问...' : '请输入您的问题...'"
							@keydown.enter.ctrl="handleSend"
						/>
						<div class="input-actions">
							<span class="tip">Ctrl + Enter 发送</span>
							<el-button type="primary" :loading="loading" @click="handleSend">
								发送
							</el-button>
						</div>
					</div>
				</div>
			</el-main>
		</el-container>
	</div>
</template>

<script lang="ts" name="AiKnowledge" setup>
import { User, ChatDotRound, Loading } from '@element-plus/icons-vue';
import { useMessage } from '/@/hooks/message';
import { ragChat, chat } from '/@/api/ai/chat';
import { documentList as fetchDocumentList } from '/@/api/ai/document';
import SourceReference from './components/SourceReference.vue';

interface Source {
	documentId: string;
	documentName: string;
	content: string;
	score: number;
}

interface KnowledgeMessage {
	id: string;
	role: 'user' | 'assistant';
	content: string;
	sources?: Source[];
	createTime: string;
}

interface Document {
	id: string;
	name: string;
	status: string;
}

const { error } = useMessage();
const messageAreaRef = ref();
const inputContent = ref('');
const loading = ref(false);
const ragMode = ref(true);
const selectedDocuments = ref<string[]>([]);
const documentList = ref<Document[]>([]);
const messageList = ref<KnowledgeMessage[]>([]);

const generateId = () => {
	return Date.now().toString(36) + Math.random().toString(36).substr(2);
};

const formatContent = (content: string) => {
	return content.replace(/\n/g, '<br>');
};

const scrollToBottom = () => {
	nextTick(() => {
		if (messageAreaRef.value) {
			messageAreaRef.value.scrollTop = messageAreaRef.value.scrollHeight;
		}
	});
};

const handleSend = async () => {
	const content = inputContent.value.trim();
	if (!content || loading.value) return;

	const userMessage: KnowledgeMessage = {
		id: generateId(),
		role: 'user',
		content,
		createTime: new Date().toISOString(),
	};
	messageList.value.push(userMessage);
	inputContent.value = '';
	scrollToBottom();

	const assistantMessage: KnowledgeMessage = {
		id: generateId(),
		role: 'assistant',
		content: '',
		createTime: new Date().toISOString(),
	};
	messageList.value.push(assistantMessage);
	loading.value = true;

	try {
		const requestData = {
			content,
			documentIds: ragMode.value ? selectedDocuments.value : undefined,
		};

		const res = ragMode.value ? await ragChat(requestData) : await chat(requestData);

		if (res.code === 0) {
			const index = messageList.value.findIndex((m) => m.id === assistantMessage.id);
			if (index !== -1) {
				messageList.value[index].content = res.data?.content || res.msg || '回复成功';
				if (ragMode.value && res.data?.sources) {
					messageList.value[index].sources = res.data.sources;
				}
			}
		} else {
			const index = messageList.value.findIndex((m) => m.id === assistantMessage.id);
			if (index !== -1) {
				messageList.value[index].content = res.msg || '请求失败';
			}
			error(res.msg || '请求失败');
		}
	} catch (err: any) {
		const index = messageList.value.findIndex((m) => m.id === assistantMessage.id);
		if (index !== -1) {
			messageList.value[index].content = '网络错误，请稍后重试';
		}
		error(err.msg || '网络错误');
	} finally {
		loading.value = false;
		scrollToBottom();
	}
};

const loadDocuments = async () => {
	try {
		const res = await fetchDocumentList({ status: 'completed' });
		if (res.code === 0) {
			documentList.value = res.data || [];
		}
	} catch (err: any) {
		error(err.msg || '加载文档列表失败');
	}
};

onMounted(() => {
	loadDocuments();
});
</script>

<style lang="scss" scoped>
.knowledge-container {
	height: 100%;
	background-color: #f5f7fa;
}

.knowledge-layout {
	height: 100%;
}

.knowledge-aside {
	background-color: #fff;
	border-right: 1px solid #e4e7ed;
	display: flex;
	flex-direction: column;
}

.aside-header {
	padding: 16px;
	border-bottom: 1px solid #e4e7ed;
	display: flex;
	justify-content: space-between;
	align-items: center;

	.title {
		font-size: 16px;
		font-weight: 600;
	}
}

.document-selector {
	padding: 16px;

	.doc-select {
		width: 100%;
	}
}

.knowledge-main {
	padding: 0;
	overflow: hidden;
}

.knowledge-content {
	display: flex;
	flex-direction: column;
	height: 100%;
}

.message-area {
	flex: 1;
	overflow-y: auto;
	padding: 20px;

	.message-item {
		display: flex;
		margin-bottom: 20px;

		&.user {
			flex-direction: row-reverse;

			.message-body {
				align-items: flex-end;
			}

			.message-content {
				background-color: #409eff;
				color: #fff;
			}
		}

		&.assistant {
			.message-content {
				background-color: #fff;
				border: 1px solid #e4e7ed;
			}
		}
	}

	.message-avatar {
		flex-shrink: 0;
		margin: 0 12px;
	}

	.message-body {
		display: flex;
		flex-direction: column;
		max-width: 70%;
	}

	.message-content {
		padding: 12px 16px;
		border-radius: 8px;
		line-height: 1.6;
		word-break: break-word;
	}

	.message-loading {
		display: flex;
		align-items: center;
		gap: 8px;
		padding: 12px 16px;
		background-color: #fff;
		border: 1px solid #e4e7ed;
		border-radius: 8px;
		color: #909399;
	}
}

.input-area {
	padding: 16px 20px;
	border-top: 1px solid #e4e7ed;
	background-color: #fff;

	.input-actions {
		display: flex;
		justify-content: space-between;
		align-items: center;
		margin-top: 12px;

		.tip {
			font-size: 12px;
			color: #909399;
		}
	}
}
</style>
