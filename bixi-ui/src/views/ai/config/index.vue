<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-card v-loading="loading" shadow="never">
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

					<el-form-item label="系统提示词" prop="systemPrompt">
						<el-input
							v-model="formData.systemPrompt"
							type="textarea"
							:rows="4"
							maxlength="4000"
							show-word-limit
						/>
					</el-form-item>

					<el-form-item>
						<el-button type="primary" :loading="saving" @click="handleSave">保存配置</el-button>
						<el-button @click="handleReset">重置</el-button>
					</el-form-item>
				</el-form>
			</el-card>
		</div>
	</div>
</template>

<script lang="ts" name="AiConfig" setup>
import { useAiStore } from '/@/stores/ai';
import { getConfig, updateConfig } from '/@/api/ai/config';
import { useMessage } from '/@/hooks/message';

const ModelSelect = defineAsyncComponent(() => import('./components/ModelSelect.vue'));
const ParamSlider = defineAsyncComponent(() => import('./components/ParamSlider.vue'));

const aiStore = useAiStore();
const { success, error } = useMessage();
const loading = ref(false);
const saving = ref(false);

const defaultConfig = {
	model: 'qwen-plus',
	temperature: 0.7,
	maxTokens: 2000,
	topP: 0.9,
	systemPrompt: '',
};

const formData = reactive({
	...defaultConfig,
});

const applyConfig = (config: any) => {
	formData.model = config?.currentModel ?? config?.model ?? defaultConfig.model;
	formData.temperature = config?.temperature ?? defaultConfig.temperature;
	formData.maxTokens = config?.maxTokens ?? defaultConfig.maxTokens;
	formData.topP = config?.topP ?? defaultConfig.topP;
	formData.systemPrompt = config?.systemPrompt ?? defaultConfig.systemPrompt;
};

const loadConfig = async () => {
	loading.value = true;
	try {
		const res = await getConfig();
		if (res.code !== 0 || !res.data) {
			error(res.msg || '加载配置失败');
			return;
		}
		applyConfig(res.data);
		aiStore.setConfig({
			model: formData.model,
			temperature: formData.temperature,
			maxTokens: formData.maxTokens,
			topP: formData.topP,
		});
	} catch (err: any) {
		error(err.msg || '加载配置失败');
	} finally {
		loading.value = false;
	}
};

const handleSave = async () => {
	saving.value = true;
	try {
		const res = await updateConfig({ ...formData });
		if (res.code !== 0) {
			error(res.msg || '配置保存失败');
			return;
		}
		aiStore.setConfig({
			model: formData.model,
			temperature: formData.temperature,
			maxTokens: formData.maxTokens,
			topP: formData.topP,
		});
		success('配置保存成功');
	} catch (err: any) {
		error(err.msg || '配置保存失败');
	} finally {
		saving.value = false;
	}
};

const handleReset = () => {
	applyConfig(defaultConfig);
	success('已恢复默认值，保存后生效');
};

onMounted(loadConfig);
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
