<template>
	<div class="message-input">
		<el-input
			v-model="inputText"
			type="textarea"
			:rows="3"
			:placeholder="placeholder"
			:disabled="disabled"
			resize="none"
			@keydown="handleKeydown"
		/>
		<div class="input-actions">
			<div class="input-tips">
				<span>Enter 发送，Shift + Enter 换行</span>
			</div>
			<el-button type="primary" :loading="loading" :disabled="!inputText.trim() || disabled" @click="handleSend">
				发送
			</el-button>
		</div>
	</div>
</template>

<script lang="ts" name="MessageInput" setup>
const inputText = ref('');

defineProps<{
	placeholder?: string;
	disabled?: boolean;
	loading?: boolean;
}>();

const emit = defineEmits<{
	(e: 'send', message: string): void;
}>();

const handleKeydown = (e: KeyboardEvent) => {
	if (e.key === 'Enter' && !e.shiftKey) {
		e.preventDefault();
		handleSend();
	}
};

const handleSend = () => {
	const text = inputText.value.trim();
	if (text) {
		emit('send', text);
		inputText.value = '';
	}
};
</script>

<style lang="scss" scoped>
.message-input {
	padding: 16px;
	background-color: #fff;
	border-top: 1px solid #e4e7ed;
}

.input-actions {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-top: 12px;
}

.input-tips {
	font-size: 12px;
	color: #909399;
}
</style>
