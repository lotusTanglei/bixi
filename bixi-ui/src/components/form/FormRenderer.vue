<template>
	<div class="form-renderer">
		<v-form-render
			ref="vFormRenderRef"
			:form-json="formJson"
			:form-data="formData"
			:option-data="optionData"
			:global-dsv="globalDsv"
			:read-only="readonly"
		/>
	</div>
</template>

<script lang="ts" name="FormRenderer" setup>
import { useMessage } from '/@/hooks/message';

interface FormSchema {
	widgetList?: any[];
	formConfig?: any;
}

const props = defineProps({
	formSchema: {
		type: Object as PropType<FormSchema>,
		default: () => ({}),
	},
	formData: {
		type: Object,
		default: () => ({}),
	},
	optionData: {
		type: Object,
		default: () => ({}),
	},
	readonly: {
		type: Boolean,
		default: false,
	},
});

const emit = defineEmits(['submit', 'validate']);

const vFormRenderRef = ref();

const formJson = computed(() => {
	if (!props.formSchema || Object.keys(props.formSchema).length === 0) {
		return {
			widgetList: [],
			formConfig: {},
		};
	}
	return props.formSchema;
});

const globalDsv = reactive({
	formConfig: {},
});

const getFormData = async () => {
	try {
		const valid = await vFormRenderRef.value?.validateForm();
		if (valid) {
			const formData = vFormRenderRef.value?.getFormData();
			emit('validate', true);
			return formData;
		} else {
			emit('validate', false);
			return null;
		}
	} catch (error) {
		emit('validate', false);
		return null;
	}
};

const validateForm = async () => {
	try {
		return await vFormRenderRef.value?.validateForm();
	} catch (error) {
		return false;
	}
};

const resetForm = () => {
	vFormRenderRef.value?.resetForm();
};

const setFormData = (data: any) => {
	vFormRenderRef.value?.setFormData(data);
};

const disableForm = () => {
	vFormRenderRef.value?.disableForm();
};

const enableForm = () => {
	vFormRenderRef.value?.enableForm();
};

const getFieldValue = (fieldName: string) => {
	return vFormRenderRef.value?.getFieldValue(fieldName);
};

const setFieldValue = (fieldName: string, value: any) => {
	vFormRenderRef.value?.setFieldValue(fieldName, value);
};

defineExpose({
	getFormData,
	validateForm,
	resetForm,
	setFormData,
	disableForm,
	enableForm,
	getFieldValue,
	setFieldValue,
});
</script>

<style lang="scss" scoped>
.form-renderer {
	width: 100%;
}
</style>
