<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-card shadow="never">
				<template #header>
					<div class="card-header">
						<span>AI 模型配置</span>
					</div>
				</template>

				<el-form ref="formRef" :model="formData" label-width="120px" style="max-width: 600px">
					<el-form-item label="模型选择" prop="model">
						<ModelSelect v-model="formData.model" placeholder="请选择AI模型" />
					</el-form-item>

					<el-form-item label="温度参数" prop="temperature">
						<ParamSlider v-model="formData.temperature" label="温度 (Temperature)" :min="0" :max="2" :step="0.1" />
						<div class="form-item-tip">控制输出的随机性，值越大输出越随机</div>
					</el-form-item>

					<el-form-item label="最大Token数" prop="maxTokens">
						<ParamSlider v-model="formData.maxTokens" label="最大Token数" :min="100" :max="8000" :step="100" />
						<div class="form-item-tip">限制单次生成的最大Token数量</div>
					</el-form-item>

					<el-form-item label="Top-P 参数" prop="topP">
						<ParamSlider v-model="formData.topP" label="Top-P" :min="0" :max="1" :step="0.1" />
						<div class="form-item-tip">核采样参数，控制生成内容的多样性</div>
					</el-form-item>

					<el-form-item>
						<el-button type="primary" @click="handleSave">保存配置</el-button>
						<el-button @click="handleReset">重置</el-button>
					</el-form-item>
				</el-form>
			</el-card>
		</div>
	</div>
</template>

<script lang="ts" name="AiConfig" setup>
import { useAiStore } from '/@/stores/ai';
import { useMessage } from '/@/hooks/message';

const ModelSelect = defineAsyncComponent(() => import('./components/ModelSelect.vue'));
const ParamSlider = defineAsyncComponent(() => import('./components/ParamSlider.vue'));

const aiStore = useAiStore();
const formRef = ref();

const defaultConfig = {
	model: 'qwen-plus',
	temperature: 0.7,
	maxTokens: 2000,
	topP: 0.9,
};

const formData = reactive({
	model: aiStore.config.model || defaultConfig.model,
	temperature: aiStore.config.temperature || defaultConfig.temperature,
	maxTokens: aiStore.config.maxTokens || defaultConfig.maxTokens,
	topP: aiStore.config.topP || defaultConfig.topP,
});

const handleSave = () => {
	aiStore.setConfig({
		model: formData.model,
		temperature: formData.temperature,
		maxTokens: formData.maxTokens,
		topP: formData.topP,
	});
	useMessage().success('配置保存成功');
};

const handleReset = () => {
	formData.model = defaultConfig.model;
	formData.temperature = defaultConfig.temperature;
	formData.maxTokens = defaultConfig.maxTokens;
	formData.topP = defaultConfig.topP;
	useMessage().success('配置已重置');
};
</script>

<style scoped>
.card-header {
	font-size: 16px;
	font-weight: 500;
}

.form-item-tip {
	font-size: 12px;
	color: var(--el-text-color-secondary);
	margin-top: 4px;
}
</style>
