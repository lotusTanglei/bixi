<template>
	<el-select v-model="selectedModel" :placeholder="placeholder" style="width: 100%">
		<el-option v-for="item in modelOptions" :key="item.value" :label="item.label" :value="item.value" />
	</el-select>
</template>

<script lang="ts" name="ModelSelect" setup>
interface ModelOption {
	value: string;
	label: string;
}

const props = withDefaults(
	defineProps<{
		modelValue: string;
		placeholder?: string;
	}>(),
	{
		modelValue: 'qwen-plus',
		placeholder: '请选择模型',
	}
);

const emit = defineEmits<{
	(e: 'update:modelValue', value: string): void;
}>();

const modelOptions: ModelOption[] = [
	{ value: 'qwen-turbo', label: 'qwen-turbo' },
	{ value: 'qwen-plus', label: 'qwen-plus' },
	{ value: 'qwen-max', label: 'qwen-max' },
	{ value: 'qwen-long', label: 'qwen-long' },
];

const selectedModel = computed({
	get: () => props.modelValue,
	set: (val: string) => emit('update:modelValue', val),
});
</script>
