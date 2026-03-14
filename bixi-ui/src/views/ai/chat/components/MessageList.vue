<template>
	<div class="message-list">
		<el-scrollbar ref="scrollbarRef" class="message-scrollbar">
			<div class="message-list-content">
				<div v-if="messages.length === 0" class="message-empty">
					<el-empty description="开始新的对话吧" />
				</div>
				<MessageItem v-for="msg in messages" :key="msg.id" :message="msg" />
			</div>
		</el-scrollbar>
	</div>
</template>

<script lang="ts" name="MessageList" setup>
import MessageItem from './MessageItem.vue';

interface Message {
	id: string;
	sessionId: string;
	role: 'user' | 'assistant';
	content: string;
	createTime: string;
}

const props = defineProps<{
	messages: Message[];
}>();

const scrollbarRef = ref();

const scrollToBottom = () => {
	nextTick(() => {
		if (scrollbarRef.value) {
			scrollbarRef.value.setScrollTop(scrollbarRef.value.wrapRef.scrollHeight);
		}
	});
};

watch(
	() => props.messages.length,
	() => {
		scrollToBottom();
	}
);

watch(
	() => props.messages,
	() => {
		scrollToBottom();
	},
	{ deep: true }
);

defineExpose({
	scrollToBottom,
});
</script>

<style lang="scss" scoped>
.message-list {
	height: 100%;
	display: flex;
	flex-direction: column;
}

.message-scrollbar {
	flex: 1;
}

.message-list-content {
	padding: 20px;
	min-height: 100%;
}

.message-empty {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 100%;
	min-height: 300px;
}
</style>
