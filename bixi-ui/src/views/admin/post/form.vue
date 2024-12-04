<template>
  <el-dialog :title="form.postId ? $t('common.editBtn') : $t('common.addBtn')" width="600" v-model="visible"
             :close-on-click-modal="false" draggable>
    <el-form ref="dataFormRef" :model="form" :rules="dataRules" label-width="90px" v-loading="loading">
      <el-form-item :label="t('post.code')" prop="code">
        <el-input v-model="form.code" :placeholder="t('post.inputPostCodeTip')"/>
      </el-form-item>
      <el-form-item :label="t('post.name')" prop="name">
        <el-input v-model="form.name" :placeholder="t('post.inputPostNameTip')"/>
      </el-form-item>
      <el-form-item :label="t('post.sn')" prop="sn">
        <el-input-number v-model="form.sn" :placeholder="t('post.inputSnTip')"/>
      </el-form-item>
      <el-form-item :label="t('post.remark')" prop="remark">
        <el-input type="textarea" maxlength="150" rows="3" v-model="form.remark" :placeholder="t('post.inputRemarkTip')"/>
      </el-form-item>
    </el-form>
    <template #footer>
			<span class="dialog-footer">
				<el-button @click="visible = false">{{ $t('common.cancelButtonText') }}</el-button>
				<el-button type="primary" @click="onSubmit" :disabled="loading">{{ $t('common.confirmButtonText') }}</el-button>
			</span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts" name="systemPostDialog">
import {useMessage} from '/@/hooks/message';
import {getObj, addObj, putObj, validatePostCode, validatePostName} from '/@/api/admin/post';
import {useI18n} from 'vue-i18n';
import {rule} from '/@/utils/validate';
// 定义子组件向父组件传值/事件
const emit = defineEmits(['refresh']);

const {t} = useI18n();

// 定义变量内容
const dataFormRef = ref();
const visible = ref(false);
const loading = ref(false);

// 提交表单数据
const form = reactive({
  id: '',
  code: '',
  name: '',
  sn: 0,
  remark: '',
});

// 定义校验规则
const dataRules = ref({
  code: [
    {validator: rule.overLength, trigger: 'blur'},
    {required: true, message: '岗位编码不能为空', trigger: 'blur'},
    {
      validator: (rule: any, value: any, callback: any) => {
        validatePostCode(rule, value, callback, form.id !== '');
      },
      trigger: 'blur',
    },
  ],
  name: [
    {validator: rule.overLength, trigger: 'blur'},
    {required: true, message: '岗位名称不能为空', trigger: 'blur'},
    {
      validator: (rule: any, value: any, callback: any) => {
        validatePostName(rule, value, callback, form.id !== '');
      },
      trigger: 'blur',
    },
  ],
  sn: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: '岗位排序不能为空', trigger: 'blur'}],
  remark: [{validator: rule.overLength, trigger: 'blur'},{required: true, message: '岗位描述不能为空', trigger: 'blur'}],
});

// 打开弹窗
const openDialog = (id: string) => {
  visible.value = true;
  form.id = '';

  // 重置表单数据
  nextTick(() => {
    dataFormRef.value?.resetFields();
  });

  // 获取Post信息
  if (id) {
    form.id = id;
    getPostData(id);
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

// 初始化表格数据
const getPostData = (id: string) => {
  // 获取部门数据
  getObj(id).then((res: any) => {
    Object.assign(form, res.data);
  });
};

// 暴露变量
defineExpose({
  openDialog,
});
</script>
