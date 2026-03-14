<template>
	<div class="layout-padding">
		<div class="layout-padding-auto layout-padding-view">
			<el-row :gutter="20">
				<el-col :span="6">
					<div class="process-category">
						<el-card shadow="never">
							<template #header>
								<div class="card-header">
									<span>流程分类</span>
								</div>
							</template>
							<el-tree
								:data="categoryTree"
								:props="{ label: 'name', children: 'children' }"
								default-expand-all
								highlight-current
								@node-click="handleNodeClick"
							/>
						</el-card>
					</div>
				</el-col>
				<el-col :span="18">
					<div class="process-list">
						<el-row :gutter="20">
							<el-col v-for="item in definitionList" :key="item.id" :span="8" class="mb20">
								<el-card shadow="hover" class="process-card" @click="handleStart(item)">
									<div class="process-card-content">
										<div class="process-icon">
											<el-icon :size="40">
												<Document />
											</el-icon>
										</div>
										<div class="process-info">
											<h3>{{ item.name }}</h3>
											<p>版本: v{{ item.version }}</p>
										</div>
									</div>
								</el-card>
							</el-col>
						</el-row>
						<el-empty v-if="definitionList.length === 0" description="暂无可发起的流程" />
					</div>
				</el-col>
			</el-row>
		</div>

		<start-dialog ref="startDialogRef" @refresh="handleStartSuccess" />
	</div>
</template>

<script lang="ts" name="workflowProcessStart" setup>
import { list as definitionListApi } from '/@/api/workflow/definition';
import { tree as categoryTreeApi } from '/@/api/workflow/category';
import { useMessage } from '/@/hooks/message';
import { useRouter } from 'vue-router';

const StartDialog = defineAsyncComponent(() => import('./start-dialog.vue'));

const router = useRouter();
const startDialogRef = ref();
const categoryTree = ref<any[]>([]);
const definitionList = ref<any[]>([]);
const currentCategory = ref('');

const getCategoryTree = async () => {
	try {
		const res = await categoryTreeApi();
		if (res.code === 0) {
			categoryTree.value = res.data || [];
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取分类树失败');
	}
};

const getDefinitionList = async () => {
	try {
		const params: any = {};
		if (currentCategory.value) {
			params.category = currentCategory.value;
		}
		const res = await definitionListApi(params);
		if (res.code === 0) {
			definitionList.value = (res.data || []).filter((item: any) => item.suspensionState === 1);
		}
	} catch (err: any) {
		useMessage().error(err.msg || '获取流程列表失败');
	}
};

const handleNodeClick = (data: any) => {
	currentCategory.value = data.id;
	getDefinitionList();
};

const handleStart = (row: any) => {
	startDialogRef.value.openDialog(row);
};

const handleStartSuccess = () => {
	router.push('/workflow/process/instance');
};

onMounted(() => {
	getCategoryTree();
	getDefinitionList();
});
</script>

<style lang="scss" scoped>
.process-category {
	.card-header {
		font-weight: bold;
	}
}

.process-card {
	cursor: pointer;
	transition: all 0.3s;

	&:hover {
		transform: translateY(-5px);
	}

	.process-card-content {
		display: flex;
		align-items: center;
		gap: 16px;

		.process-icon {
			display: flex;
			align-items: center;
			justify-content: center;
			width: 60px;
			height: 60px;
			background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
			border-radius: 12px;
			color: #fff;
		}

		.process-info {
			flex: 1;

			h3 {
				margin: 0 0 8px 0;
				font-size: 16px;
				color: #303133;
			}

			p {
				margin: 0;
				font-size: 12px;
				color: #909399;
			}
		}
	}
}
</style>
