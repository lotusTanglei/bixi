<template>
  <el-form :model="dataForm" :rules="dataRules" label-width="120px" ref="dataFormRef" v-loading="loading">
    <el-row>
      <el-col :span="12" class="mb20">
        <el-form-item label="表名" prop="tableName">
          <el-input disabled placeholder="表名" :value="tableNameStr"></el-input>
        </el-form-item>
      </el-col>
      <el-col :span="12" class="mb20">
        <el-form-item prop="tableComment">
          <template #label>
            <span>注释</span>
            <tip content="注释"/>
          </template>
          <el-input placeholder="说明" v-model="dataForm.tableComment"></el-input>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row>
      <el-col :span="12" class="mb20">
        <el-form-item label="类名" prop="className">
          <el-input placeholder="类名" v-model="dataForm.className"></el-input>
        </el-form-item>
      </el-col>
      <el-col :span="12" class="mb20">
        <el-form-item label="作者" prop="author">
          <el-input placeholder="默认作者" v-model="dataForm.author"></el-input>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row>
      <el-col :span="12" class="mb20">
        <el-form-item label="项目包名" prop="packageName">
          <el-input placeholder="项目包名" v-model="dataForm.packageName"></el-input>
        </el-form-item>
      </el-col>
      <el-col :span="12" class="mb20">
        <el-form-item prop="moduleName">
          <template #label>
            <span>模块名</span>
            <tip content="所属微服务模块名称，对应微服务路由前缀 （单体固定 admin）"/>
          </template>
          <el-input placeholder="模块名" v-model="dataForm.moduleName"></el-input>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row>
      <el-col :span="12" class="mb20">
        <el-form-item prop="functionName">
          <template #label>
            <span>功能名</span>
            <tip content="对应生成的Controller @RequestMapping 请求路径"/>
          </template>
          <el-input placeholder="功能名" v-model="dataForm.functionName"></el-input>
        </el-form-item>
      </el-col>
      <el-col :span="12" class="mb20">
        <el-form-item label="代码风格" prop="style">
          <el-select v-model="dataForm.style">
            <el-option :key="index" :label="item.groupName" :value="item.id"
                       v-for="(item, index) in groupDataList"></el-option>
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row>
      <el-col :span="12" class="mb20">
        <el-form-item label="表单布局" prop="formLayout">
          <el-radio-group v-model="dataForm.formLayout">
            <el-radio border :value="1">一列</el-radio>
            <el-radio border :value="2">两列</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-col>
      <el-col :span="12" class="mb20">
        <el-form-item label="生成方式" prop="generatorType">
          <el-radio-group v-model="dataForm.generatorType">
            <el-radio border value="1">自定义路径</el-radio>
            <el-radio border value="0">ZIP 压缩包</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-col>
      <el-col :span="24" class="mb20">
		<el-form-item label="覆盖文件" v-if="dataForm.generatorType === '1'">
		  <el-checkbox v-model="dataForm.overwrite">允许覆盖已有生成文件</el-checkbox>
		</el-form-item>
      </el-col>
    </el-row>
	<el-divider content-position="left">父子关系（可选）</el-divider>
	<el-alert v-if="relationshipError" class="mb20" :closable="false" show-icon :title="relationshipError" type="error" />
	<el-row :gutter="16">
	  <el-col :xs="24" :sm="12" :md="8" class="mb20">
		<el-form-item label="子表" prop="childTableName">
		  <el-select v-model="dataForm.childTableName" clearable filterable placeholder="选择子表" @change="handleChildTableChange">
			<el-option v-for="table in childTableOptions" :key="table" :label="table" :value="table" />
		  </el-select>
		</el-form-item>
	  </el-col>
	  <el-col :xs="24" :sm="12" :md="8" class="mb20">
		<el-form-item label="主表关联键" prop="mainField">
		  <el-select v-model="dataForm.mainField" :disabled="!dataForm.childTableName" placeholder="选择主键" @change="handleMainFieldChange">
			<el-option v-for="field in mainFieldOptions" :key="field.fieldName" :label="fieldLabel(field)" :value="field.fieldName" />
		  </el-select>
		</el-form-item>
	  </el-col>
	  <el-col :xs="24" :sm="12" :md="8" class="mb20">
		<el-form-item label="子表关联键" prop="childField">
		  <el-select v-model="dataForm.childField" :disabled="!dataForm.mainField" :loading="childLoading" filterable placeholder="选择关联字段">
			<el-option v-for="field in childFieldOptions" :key="field.fieldName" :label="fieldLabel(field)" :value="field.fieldName" />
		  </el-select>
		</el-form-item>
	  </el-col>
	</el-row>
  </el-form>
</template>

<script lang="ts" setup>
import {putObj, useListTableApi, useTableApi} from '/@/api/gen/table';
import {list as groupList} from '/@/api/gen/group';
import {checkVersion, online} from '/@/api/gen/template';
import {rule} from "/@/utils/validate";
import {useMessage, useMessageBox} from "/@/hooks/message";

const props = defineProps({
  tableName: {
    type: String,
  },
  dsName: {
    type: String,
  },
});

const emit = defineEmits(['refreshDataList']);
const visible = ref(false);
const loading = ref(false);
const childLoading = ref(false);
const dataFormRef = ref();
const relationshipError = ref('');

interface GeneratorField {
  fieldName: string;
  fieldComment?: string;
  attrType?: string;
  primaryPk?: string;
}

const dataForm = reactive({
  id: '',
  generatorType: '0',
  formLayout: 1,
  backendPath: '',
  frontendPath: '',
	overwrite: false,
  packageName: '',
  email: '',
  author: '',
  version: '',
  moduleName: '',
  functionName: '',
  className: '',
  tableComment: '',
  tableName: '' as string,
  dsName: '' as string,
  style: '', //  默认风格 element-plus
  childTableName: '',
	mainField: '',
	childField: '',
});

const groupDataList = ref<any[]>([]);
const tableOptions = ref<string[]>([]);
const parentFields = ref<GeneratorField[]>([]);
const childFields = ref<GeneratorField[]>([]);
const managedRelationshipFields = new Set([
  'id', 'tenant_id', 'create_by', 'create_time', 'update_by', 'update_time', 'del_flag',
]);
let childFieldsRequestId = 0;
const tableNameStr = computed(() => dataForm.childTableName
  ? `${props.tableName} + ${dataForm.childTableName}`
  : String(props.tableName || ''));
const childTableOptions = computed(() => tableOptions.value.filter((table) => table !== props.tableName));
const mainFieldOptions = computed(() => parentFields.value.filter((field) => field.primaryPk === '1'
  && field.fieldName.toLowerCase() === 'id' && field.attrType === 'Long'));
const selectedMainField = computed(() => parentFields.value.find((field) => field.fieldName === dataForm.mainField));
const childFieldOptions = computed(() => childFields.value.filter((field) => field.primaryPk !== '1'
  && field.attrType === 'Long' && !managedRelationshipFields.has(field.fieldName.toLowerCase())
  && (!selectedMainField.value?.attrType || field.attrType === selectedMainField.value.attrType)));
const fieldLabel = (field: GeneratorField) => field.fieldComment
  ? `${field.fieldName} (${field.fieldComment})`
  : field.fieldName;

const loadChildFields = async (tableName: string) => {
  const requestId = ++childFieldsRequestId;
  childFields.value = [];
  relationshipError.value = '';
  if (!tableName) {
    childLoading.value = false;
    return;
  }
  childLoading.value = true;
  try {
    const response = await useTableApi(String(props.dsName), tableName);
    if (requestId !== childFieldsRequestId) return;
    childFields.value = response.data.fieldList || [];
  } catch (error: any) {
    if (requestId !== childFieldsRequestId) return;
    relationshipError.value = error?.msg || '子表字段加载失败';
  } finally {
    if (requestId === childFieldsRequestId) childLoading.value = false;
  }
};

const handleChildTableChange = async (tableName: string) => {
  dataForm.mainField = '';
  dataForm.childField = '';
  await loadChildFields(tableName);
};
const handleMainFieldChange = () => {
  if (!childFieldOptions.value.some((field) => field.fieldName === dataForm.childField)) {
    dataForm.childField = '';
  }
};

const getTable = async (dsName: string, tableName: string) => {
  loading.value = true;
  try {
    const res = await useTableApi(dsName, tableName);
    Object.assign(dataForm, res.data);
    parentFields.value = res.data.fieldList || [];
    const list = res.data.groupList || [];
    dataForm.style = dataForm.style || list[0]?.id;
    if (dataForm.childTableName) await loadChildFields(dataForm.childTableName);
  } finally {
    loading.value = false;
  }
};

const getTableOptions = async () => {
  const response = await useListTableApi(String(props.dsName));
  tableOptions.value = response.data || [];
};

const validateRelationship = (_rule: unknown, _value: unknown, callback: (error?: Error) => void) => {
  const configured = [dataForm.childTableName, dataForm.mainField, dataForm.childField].filter(Boolean).length;
  if (configured !== 0 && configured !== 3) return callback(new Error('子表、主表关联键和子表关联键必须同时配置'));
  if (dataForm.childTableName === dataForm.tableName) return callback(new Error('子表不能与主表相同'));
  const main = parentFields.value.find((field) => field.fieldName === dataForm.mainField);
  const child = childFields.value.find((field) => field.fieldName === dataForm.childField);
  if (configured === 3 && !mainFieldOptions.value.includes(main as GeneratorField)) {
    return callback(new Error('主表关联键必须是 Long 类型的 id 主键'));
  }
  if (configured === 3 && !childFieldOptions.value.includes(child as GeneratorField)) {
    return callback(new Error('子表关联键必须是非基础字段的 Long 类型非主键'));
  }
  callback();
};

const dataRules = ref({
  tableName: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  tableComment: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  className: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  packageName: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  author: [{validator: rule.overLength, trigger: 'blur'}, {required: true, message: '必填项不能为空', trigger: 'blur'}],
  moduleName: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  functionName: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  generatorType: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  formLayout: [{validator: rule.overLength, trigger: 'blur'}, {
    required: true,
    message: '必填项不能为空',
    trigger: 'blur'
  }],
  style: [{required: true, message: '必填项不能为空', trigger: 'blur'}],
	childTableName: [{validator: validateRelationship, trigger: 'change'}],
	mainField: [{validator: validateRelationship, trigger: 'change'}],
	childField: [{validator: validateRelationship, trigger: 'change'}],
});

const buildTableConfigurationPayload = () => ({
  ...dataForm,
  childTableName: dataForm.childTableName,
  mainField: dataForm.mainField,
  childField: dataForm.childField,
});

// 保存
const submitHandle = async () => {
  try {
    const valid = await dataFormRef.value.validate(); // 表单校验
    if (!valid) return false;

    loading.value = true;
    const payload = buildTableConfigurationPayload();
    await putObj(payload);
    visible.value = false;
    emit('refreshDataList');
    return payload;
  } catch {
    return Promise.reject();
  } finally {
	loading.value = false;
  }
};

const genGroupList = () => {
  groupList().then(({data}) => {
    if (data && data.length > 0 ){
      groupDataList.value = data;
    }
  })
};

/**
 * 检查模板版本
 */
const checkTemplateVersion = async () => {
  checkVersion().then(({data}) => {
    if (!data) {
      useMessageBox().confirm('模板发现新版本，是否更新？').then(() => {
        // 更新模板
        online().then(() => {
          useMessage().success('更新成功');
          genGroupList()
        });
      }).catch(() => {
      })
    }
  })
};

onMounted(async () => {
  // 重置表单数据
  if (dataFormRef.value) {
    dataFormRef.value.resetFields();
  }
  dataForm.id = '';
  dataForm.tableName = String(props.tableName);
  dataForm.dsName = String(props.dsName);

	await Promise.all([
	  getTable(dataForm.dsName, dataForm.tableName),
	  getTableOptions(),
	]);
  genGroupList();
  checkTemplateVersion()
});

defineExpose({
  submitHandle,
});
</script>

<style lang="scss" scoped>
.generator-code .el-dialog__body {
  padding: 15px 30px 0 20px;
}
</style>
