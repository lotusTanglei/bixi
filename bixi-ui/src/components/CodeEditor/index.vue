<template>
	<div class="code-editor" :style="{ height: _height }">
		<textarea ref="textarea" v-model="contentValue"></textarea>
	</div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, markRaw } from 'vue';

//框架
import CodeMirror from 'codemirror';
import 'codemirror/lib/codemirror.css';

//主题
import 'codemirror/theme/idea.css';
import 'codemirror/theme/darcula.css';

//功能
import 'codemirror/addon/selection/active-line';

//语言
import 'codemirror/mode/velocity/velocity';
import 'codemirror/mode/go/go';

interface Props {
	modelValue?: string;
	mode?: string;
	height?: string | number;
	options?: Record<string, any>;
	theme?: string;
	readOnly?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
	modelValue: '',
	mode: 'go',
	height: 300,
	options: () => ({}),
	theme: 'idea',
	readOnly: false,
});

const emit = defineEmits<{
	(e: 'update:modelValue', value: string): void;
}>();

const textarea = ref<HTMLTextAreaElement>();
const contentValue = ref(props.modelValue);
const coder = ref<any>(null);

const opt = {
	theme: props.theme, //主题
	styleActiveLine: true, //高亮当前行
	lineNumbers: true, //行号
	lineWrapping: false, //自动换行
	tabSize: 4, //Tab缩进
	indentUnit: 4, //缩进单位
	indentWithTabs: true, //自动缩进
	mode: props.mode, //语言
	readOnly: props.readOnly, //只读
	...props.options,
};

const _height = computed(() => {
	return Number(props.height) ? Number(props.height) + 'px' : props.height;
});

watch(
	() => props.modelValue,
	(val) => {
		contentValue.value = val;
		if (val !== coder.value?.getValue()) {
			coder.value?.setValue(val);
		}
	},
);

const init = () => {
	coder.value = markRaw(CodeMirror.fromTextArea(textarea.value!, opt));
	coder.value.on('change', (coder: any) => {
		contentValue.value = coder.getValue();
		emit('update:modelValue', contentValue.value);
	});
};

const formatStrInJson = (strValue: string) => {
	return JSON.stringify(JSON.parse(strValue), null, 4);
};

onMounted(() => {
	init();
	//获取挂载的所有modes
	//console.log(CodeMirror.modes)
});

defineExpose({
	formatStrInJson,
});
</script>

<style scoped>
.code-editor {
	font-size: 14px;
	border: 1px solid #ddd;
	line-height: 150%;
}
.code-editor:deep(.CodeMirror) {
	height: 100%;
}
</style>
