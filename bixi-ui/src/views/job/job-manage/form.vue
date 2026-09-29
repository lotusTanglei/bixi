<template>
		<el-dialog v-model="visible" :close-on-click-modal="false" :title="form.id ? $t('common.editBtn') : $t('common.addBtn')" draggable>
		<el-form ref="dataFormRef" :model="form" :rules="dataRules" formDialogRef label-width="120px" v-loading="loading">
      <el-row :gutter="20">
        <el-col :span="12" class="mb20">
          <el-form-item :label="t('job.jobName')" prop="name">
            <el-input v-model="form.name" :placeholder="t('job.inputjobNameTip')"/>
          </el-form-item>
        </el-col>
        <el-col :span="12" class="mb20">
          <el-form-item :label="t('job.jobGroup')" prop="group">
            <el-input v-model="form.group" :placeholder="t('job.inputjobGroupTip')"/>
          </el-form-item>
        </el-col>

        <el-col :span="12" class="mb20">
          <el-form-item :label="t('job.jobType')" prop="type">
            <el-select v-model="form.type" :placeholder="t('job.jobType')">
              <el-option v-for="(item, index) in job_type" :key="index" :label="item.label"
                         :value="item.value"></el-option>
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12" class="mb20" v-if="['3', '4'].includes(form.type)">
          <el-form-item :label="t('job.executePath')" prop="executePath">
            <el-input v-model="form.executePath" :placeholder="t('job.inputexecutePathTip')"/>
          </el-form-item>
        </el-col>

        <el-col :span="12" class="mb20" v-if="['1', '2'].includes(form.type)">
          <el-form-item :label="t('job.className')" prop="className">
            <el-input v-model="form.className" :placeholder="t('job.inputclassNameTip')"/>
          </el-form-item>
        </el-col>

        <el-col :span="12" class="mb20" v-if="['1', '2'].includes(form.type)">
          <el-form-item :label="t('job.methodName')" prop="methodName">
            <el-input v-model="form.methodName" :placeholder="t('job.inputmethodNameTip')"/>
          </el-form-item>
        </el-col>

        <el-col :span="12" class="mb20">
          <el-form-item :label="t('job.methodParamsValue')" prop="methodParamsValue">
            <el-input v-model="form.methodParamsValue" :placeholder="t('job.inputmethodParamsValueTip')"/>
          </el-form-item>
        </el-col>

        <el-col :span="12" class="mb20">
          <el-form-item :label="t('job.cronExpression')" prop="cronExpression">
            <crontab clearable @hide="popoverVis(false)" v-model="form.cronExpression"></crontab>
          </el-form-item>
        </el-col>

	        <el-col :span="12" class="mb20">
	          <el-form-item :label="t('job.misfirePolicy')" prop="misfirePolicy">
            <el-select v-model="form.misfirePolicy" :placeholder="t('job.inputmisfirePolicyTip')">
              <el-option v-for="(item, index) in misfire_policy" :key="index" :label="item.label"
                         :value="item.value"></el-option>
            </el-select>
	          </el-form-item>
	        </el-col>
	        <el-col :span="12" class="mb20">
	          <el-form-item :label="t('job.retryCount')" prop="retryCount">
	            <el-input-number v-model="form.retryCount" :min="0" :max="5" :step="1" controls-position="right" />
	          </el-form-item>
	        </el-col>
	        <el-col :span="12" class="mb20">
	          <el-form-item :label="t('job.retryIntervalSeconds')" prop="retryIntervalSeconds">
	            <el-input-number v-model="form.retryIntervalSeconds" :min="1" :max="300" :step="1" controls-position="right" />
	          </el-form-item>
	        </el-col>
	        <el-col :span="24" class="mb20">
          <el-form-item :label="t('job.remark')" prop="remark">
            <el-input v-model="form.remark" :placeholder="t('job.inputremarkTip')" type="textarea"/>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
			<span class="dialog-footer">
				<el-button formDialogRef @click="visible = false">{{ $t('common.cancelButtonText') }}</el-button>
				<el-button formDialogRef type="primary" @click="onSubmit" :disabled="loading">{{
            $t('common.confirmButtonText')
          }}</el-button>
			</span>
    </template>
  </el-dialog>
</template>

<script lang="ts" name="SysJobDialog" setup>
// 定义子组件向父组件传值/事件
import {useDict} from '/@/hooks/dict';
import {useMessage} from '/@/hooks/message';
import {addObj, getObj, putObj} from '/@/api/job/job';
import {useI18n} from 'vue-i18n';
import {rule} from '/@/utils/validate';
const emit = defineEmits(['refresh']);
const Crontab = defineAsyncComponent(() => import('/@/components/Crontab/index.vue'));

const {t} = useI18n();

// 定义变量内容
const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

// 定义字典
const {misfire_policy, job_type} = useDict('job_status', 'job_execute_status', 'misfire_policy', 'job_type');

// 提交表单数据
const initialForm = () => ({
  id: '',
  name: '',
  group: '',
  type: '',
  executePath: '',
  className: '',
  methodName: '',
  methodParamsValue: '',
  cronExpression: '',
  misfirePolicy: '3',
  retryCount: 0,
  retryIntervalSeconds: 5,
  remark: '',
});
const form = reactive(initialForm());

const popoverVis = (bol: boolean) => {
  popoverVisible.value = bol;
};

const popoverVisible = ref(false);
// 定义校验规则
const requiredForType = (types: string[], message: string) => (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (types.includes(form.type) && !value?.trim()) {
    callback(new Error(message));
    return;
  }
  callback();
};

const dataRules = reactive({
  name: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: '任务名称不能为空', trigger: 'blur'}],
  group: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: '任务组名不能为空', trigger: 'blur'}],
  type: [{required: true, message: '任务类型不能为空', trigger: 'blur'}],
  cronExpression: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: 'cron不能为空', trigger: 'blur'}],
  misfirePolicy: [{required: true, message: '策略不能为空', trigger: 'blur'}],
  executePath: [{validator: rule.overLength, trigger: 'blur'}, {validator: requiredForType(['3', '4'], '执行路径不能为空'), trigger: 'blur'}],
  className: [{validator: rule.overLength, trigger: 'blur'}, {validator: requiredForType(['1', '2'], '执行文件不能为空'), trigger: 'blur'}],
  methodName: [{validator: rule.overLength, trigger: 'blur'}, {validator: requiredForType(['1', '2'], '执行方法不能为空'), trigger: 'blur'}],
  methodParamsValue: [{validator: rule.overLength, trigger: 'blur'}],
  retryCount: [{required: true, type: 'number', min: 0, max: 5, message: '重试次数必须在0到5之间', trigger: 'change'}],
  retryIntervalSeconds: [{required: true, type: 'number', min: 1, max: 300, message: '重试间隔必须在1到300秒之间', trigger: 'change'}],
});

// 打开弹窗
const openDialog = (id: string) => {
  visible.value = true;
  Object.assign(form, initialForm());

	  // 重置表单数据
	  nextTick(() => {
	    dataFormRef.value?.clearValidate();
	  });

  // 获取sysJob信息
  if (id) {
    form.id = id;
    getsysJobData(id);
  }
};

// 提交
const onSubmit = async () => {
  const valid = await dataFormRef.value.validate().catch(() => {
  });
  if (!valid) return false;

  try {
    loading.value = true;
    const payload = {
      id: form.id || undefined,
      name: form.name,
      group: form.group,
      type: form.type,
      executePath: ['3', '4'].includes(form.type) ? form.executePath : '',
      className: ['1', '2'].includes(form.type) ? form.className : '',
      methodName: ['1', '2'].includes(form.type) ? form.methodName : '',
      methodParamsValue: form.methodParamsValue,
      cronExpression: form.cronExpression,
      misfirePolicy: form.misfirePolicy,
      retryCount: form.retryCount,
      retryIntervalSeconds: form.retryIntervalSeconds,
      remark: form.remark,
    };
    form.id ? await putObj(payload) : await addObj(payload);
    useMessage().success(t(form.id ? 'common.editSuccessText' : 'common.addSuccessText'));
    visible.value = false;
    emit('refresh');
  } catch (err: any) {
    useMessage().error('任务初始化异常');
  } finally {
    loading.value = false;
  }
};

// 初始化表单数据
const getsysJobData = (id: string) => {
	  // 获取数据
	  getObj(id).then((res: any) => {
	    const data = res.data ?? {};
	    Object.assign(form, {
	      id: data.id ?? id,
	      name: data.name ?? '',
	      group: data.group ?? '',
	      type: data.type ?? '',
	      executePath: data.executePath ?? '',
	      className: data.className ?? '',
	      methodName: data.methodName ?? '',
	      methodParamsValue: data.methodParamsValue ?? '',
	      cronExpression: data.cronExpression ?? '',
	      misfirePolicy: data.misfirePolicy ?? '3',
	      retryCount: data.retryCount ?? 0,
	      retryIntervalSeconds: data.retryIntervalSeconds ?? 5,
	      remark: data.remark ?? '',
	    });
	  });
};

// 暴露变量
defineExpose({
  openDialog,
});
</script>
