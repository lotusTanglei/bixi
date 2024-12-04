<template>
  <el-dialog :close-on-click-modal="false" :title="form.id ? $t('common.editBtn') : $t('common.addBtn')"
             width="600" draggable v-model="visible">
    <el-form :model="form" :rules="dataRules" formDialogRef label-width="90px" ref="dataFormRef" v-loading="loading">
      <el-form-item :label="t('param.systemFlag')" prop="systemFlag">
        <el-radio-group v-model="form.systemFlag">
          <el-radio :label="item.value" border v-for="(item, index) in dict_type" :key="index">{{
              item.label
            }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item :label="t('param.type')" prop="type">
        <el-select :placeholder="t('param.inputPublicParamTypeTip')" v-model="form.type">
          <el-option :key="index" :label="item.label" :value="item.value"
                     v-for="(item, index) in param_type"></el-option>
        </el-select>
      </el-form-item>
      <el-form-item :label="t('param.validateCode')" prop="validateCode">
        <el-input :placeholder="t('param.inputValidateCodeTip')" v-model="form.validateCode"/>
      </el-form-item>
      <el-form-item :label="t('param.name')" prop="name">
        <el-input :placeholder="t('param.inputPublicParamNameTip')" v-model="form.name"/>
      </el-form-item>
      <el-form-item :label="t('param.key')" prop="key">
        <el-input :placeholder="t('param.inputPublicParamKeyTip')" v-model="form.key"/>
      </el-form-item>
      <el-form-item :label="t('param.value')" prop="value">
        <el-input :placeholder="t('param.inputPublicParamValueTip')" v-model="form.value"/>
      </el-form-item>
      <el-form-item :label="$t('param.sn')" prop="sn">
        <el-input-number v-model="form.sn" :placeholder="$t('param.inputSnTip')" clearable/>
      </el-form-item>
      <el-form-item :label="t('param.status')" prop="status">
        <el-radio-group v-model="form.status">
          <el-radio :label="item.value" border v-for="(item, index) in status_type" :key="index">{{
              item.label
            }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer>
			<span class="dialog-footer">
				<el-button @click="visible = false">{{ $t('common.cancelButtonText') }}</el-button>
				<el-button @click="onSubmit" type="primary" :disabled="loading">{{ $t('common.confirmButtonText') }}</el-button>
			</span>
    </template>
  </el-dialog>
</template>

<script lang="ts" name="SysPublicParamDialog" setup>
// 定义子组件向父组件传值/事件
import {useDict} from '/@/hooks/dict';
import {useMessage} from '/@/hooks/message';
import {addObj, getObj, putObj, validateParamsCode, validateParamsName} from '/@/api/admin/param';
import {useI18n} from 'vue-i18n';
import {rule} from '/@/utils/validate';

const emit = defineEmits(['refresh']);

const {t} = useI18n();

// 定义变量内容
const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

// 定义字典
const {dict_type, status_type, param_type} = useDict('dict_type', 'status_type', 'param_type');

// 提交表单数据
const form = reactive({
  id: '',
  name: '',
  key: '',
  value: '',
  status: '0',
  validateCode: '',
  type: '0',
  systemFlag: '0',
  sn: 0,
});

// 定义校验规则
const dataRules = reactive({
  name: [
    {validator: rule.overLength, trigger: 'blur'},
    {required: true, message: '名称不能为空', trigger: 'blur'},
    {
      validator: (rule: any, value: any, callback: any) => {
        validateParamsName(rule, value, callback, form.id !== '');
      },
      trigger: 'blur',
    },
  ],
  key: [
    {validator: rule.overLength, trigger: 'blur'},
    {required: true, message: '参数键不能为空', trigger: 'blur'},
    {validator: rule.validatorCapital, trigger: 'blur'},
    {
      validator: (rule: any, value: any, callback: any) => {
        validateParamsCode(rule, value, callback, form.id !== '');
      },
      trigger: 'blur',
    },
  ],
  value: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: '参数值不能为空', trigger: 'blur'}],
  status: [{required: true, message: '状态不能为空', trigger: 'blur'}],
  type: [{required: true, message: '类型不能为空', trigger: 'blur'}],
  systemFlag: [{required: true, message: '类型不能为空', trigger: 'blur'}],
  validateCode: [{validator: rule.overLength, trigger: 'blur'}],
  sn: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: '排序不能为空', trigger: 'blur'}],
});

// 打开弹窗
const openDialog = (id: string) => {
  visible.value = true;
  form.id = '';

  // 重置表单数据
  nextTick(() => {
    dataFormRef.value?.resetFields();
  });

  // 获取sysPublicParam信息
  if (id) {
    form.id = id;
    getsysPublicParamData(id);
  }
};

// 提交
const onSubmit = async () => {
  const valid = await dataFormRef.value.validate().catch(() => {
  });
  if (!valid) return false;

  try {
    loading.value = true;
    form.id ? await putObj(form) : await addObj(form);
    useMessage().success(t(form.id ? 'common.editSuccessText' : 'common.addSuccessText'));
    visible.value = false;
    emit('refresh');
  } catch (err: any) {
    useMessage().error(err.msg);
  } finally {
    loading.value = false;
  }
};

// 初始化表单数据
const getsysPublicParamData = (id: string) => {
  // 获取数据
  getObj(id).then((res: any) => {
    Object.assign(form, res.data);
  });
};

// 暴露变量
defineExpose({
  openDialog,
});
</script>
