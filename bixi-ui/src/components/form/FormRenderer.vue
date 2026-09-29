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
	permissions: {
		type: Object as PropType<Record<string, string>>,
		default: () => ({}),
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

const validateForm = () =>
	new Promise<boolean>((resolve) => {
		const renderer = vFormRenderRef.value;
		if (!renderer) {
			resolve(false);
			return;
		}
		renderer.validateForm((valid: boolean) => resolve(valid));
	});

const editableData = (source: Record<string, any> = {}) => {
	const entries = Object.entries(props.permissions);
	const data =
		entries.length === 0
			? source
			: Object.fromEntries(
					entries
						.filter(([, permission]) => permission === 'edit')
						.filter(([field]) => Object.prototype.hasOwnProperty.call(source, field))
						.map(([field]) => [field, source[field]])
				);
	return JSON.parse(JSON.stringify(data));
};

const getFormDataSnapshot = () => editableData(vFormRenderRef.value?.getFormData(false) || {});

const getFormData = async () => {
	const valid = await validateForm().catch(() => false);
	emit('validate', valid);
	return valid ? getFormDataSnapshot() : null;
};

const applyPermissions = async () => {
	await nextTick();
	const renderer = vFormRenderRef.value;
	if (!renderer) return;
	if (props.readonly) renderer.disableForm();
	else renderer.enableForm();
	const disabled = Object.entries(props.permissions)
		.filter(([, permission]) => permission !== 'edit')
		.map(([field]) => field);
	const hidden = Object.entries(props.permissions)
		.filter(([, permission]) => permission === 'hidden')
		.map(([field]) => field);
	if (disabled.length > 0) renderer.disableWidgets(disabled);
	if (hidden.length > 0) renderer.hideWidgets(hidden);
};

watch(() => [props.formSchema, props.permissions, props.readonly], applyPermissions, { deep: true, immediate: true, flush: 'post' });

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
	getFormDataSnapshot,
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
