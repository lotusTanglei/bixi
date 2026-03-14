<template>
	<div class="param-slider">
		<div class="param-slider__header">
			<span class="param-slider__label">{{ label }}</span>
			<span class="param-slider__value">{{ modelValue }}</span>
		</div>
		<el-slider v-model="sliderValue" :min="min" :max="max" :step="step" show-input />
	</div>
</template>

<script lang="ts" name="ParamSlider" setup>
const props = withDefaults(
	defineProps<{
		label: string;
		min: number;
		max: number;
		step: number;
		modelValue: number;
	}>(),
	{
		label: '',
		min: 0,
		max: 100,
		step: 1,
		modelValue: 0,
	}
);

const emit = defineEmits<{
	(e: 'update:modelValue', value: number): void;
}>();

const sliderValue = computed({
	get: () => props.modelValue,
	set: (val: number) => emit('update:modelValue', val),
});
</script>

<style scoped>
.param-slider {
	width: 100%;
}

.param-slider__header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	margin-bottom: 8px;
}

.param-slider__label {
	font-size: 14px;
	color: var(--el-text-color-regular);
}

.param-slider__value {
	font-size: 14px;
	font-weight: 500;
	color: var(--el-color-primary);
}
</style>
