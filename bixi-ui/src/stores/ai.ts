import { defineStore } from 'pinia';

interface Session {
	id: string;
	title: string;
	createTime: string;
}

interface Message {
	id: string;
	sessionId: string;
	role: 'user' | 'assistant';
	content: string;
	createTime: string;
}

interface AiConfig {
	model: string;
	temperature: number;
	maxTokens: number;
	topP: number;
}

interface AiState {
	currentSession: Session | null;
	sessionList: Session[];
	messageList: Message[];
	config: AiConfig;
	loading: boolean;
}

export const useAiStore = defineStore('ai', {
	state: (): AiState => ({
		currentSession: null,
		sessionList: [],
		messageList: [],
		config: {
			model: 'qwen-plus',
			temperature: 0.7,
			maxTokens: 2000,
			topP: 0.9,
		},
		loading: false,
	}),

	getters: {
		hasSession: (state) => !!state.currentSession,
		messageCount: (state) => state.messageList.length,
	},

	actions: {
		setCurrentSession(session: Session | null) {
			this.currentSession = session;
		},

		setSessionList(list: Session[]) {
			this.sessionList = list;
		},

		addSession(session: Session) {
			this.sessionList.unshift(session);
			this.currentSession = session;
		},

		removeSession(sessionId: string) {
			this.sessionList = this.sessionList.filter((s) => s.id !== sessionId);
			if (this.currentSession?.id === sessionId) {
				this.currentSession = this.sessionList[0] || null;
			}
		},

		updateSessionTitle(sessionId: string, title: string) {
			const session = this.sessionList.find((s) => s.id === sessionId);
			if (session) {
				session.title = title;
			}
		},

		setMessageList(list: Message[]) {
			this.messageList = list;
		},

		addMessage(message: Message) {
			this.messageList.push(message);
		},

		updateMessage(messageId: string, content: string) {
			const message = this.messageList.find((m) => m.id === messageId);
			if (message) {
				message.content = content;
			}
		},

		clearMessages() {
			this.messageList = [];
		},

		setConfig(config: Partial<AiConfig>) {
			this.config = { ...this.config, ...config };
		},

		setLoading(loading: boolean) {
			this.loading = loading;
		},
	},

	persist: {
		key: 'ai-store',
		paths: ['config'],
	},
});
