<template>
	<div :class="mode == 'square' ? 'chatface' : 'brround avatar cover-image'" :style="transform" style="overflow: hidden; width: 40px; height: 40px">
		<img v-if="faceUrl && !num" :src="faceUrl" class="w-100 h-100" />
		<div v-else :style="styles" class="w-100 h-100 d-flex ai-center jc-center">{{ text }}</div>
	</div>
</template>

<script setup lang="ts">
import { computed } from 'vue';

interface Props {
	scale?: string;
	num?: number | string;
	name?: string;
	mode?: string;
	fontColor?: string;
	backgroundColor?: string;
	faceUrl?: string;
}

const props = withDefaults(defineProps<Props>(), {
	scale: '1',
	mode: '',
	fontColor: '#fff',
	backgroundColor: '#F04A2A',
	faceUrl: '',
});

const text = computed(() => {
	if (props.num !== undefined) {
		return `+${props.num}`;
	} else if (props.name) {
		return props.name.slice(-2);
	}
	return '';
});

const transform = computed(() => {
	const style: Record<string, string> = {};
	if (props.scale) {
		style.transform = `scale(${props.scale}, ${props.scale})`;
	}
	return style;
});

const styles = computed(() => {
	const style: Record<string, string> = {};
	if (props.size) {
		style['font-size'] = '12px';
	}
	if (props.fontColor) {
		style.color = props.fontColor;
	}
	if (props.backgroundColor) {
		style.background = props.backgroundColor;
	}
	return style;
});
</script>

<style lang="scss" scoped>
@import './base.scss';

.avatar {
	display: inline-block;
	position: relative;
	text-align: center;
	vertical-align: bottom;
	font-size: 8px;
	user-select: none;
	z-index: 10;

	&:hover {
		z-index: 100;
	}
}

.brround {
	border-radius: 50%;
}

.chatface {
	display: block;
	border-radius: 8px;
	overflow: hidden;

	img {
		width: 100%;
		height: 100%;
	}
}
</style>
