<template>
	<div class="form-table" ref="scFormTable">
		<el-table
			:data="data"
			ref="table"
			border
			stripe
			:cell-style="{ textAlign: 'center' }"
			:header-cell-style="{
				textAlign: 'center',
				background: 'var(--el-table-row-hover-bg-color)',
				color: 'var(--el-text-color-primary)',
			}"
		>
			<el-table-column type="index" width="50" fixed="left">
				<template #header>
					<el-button v-if="!hideAdd" type="primary" icon="el-icon-plus" size="small" circle @click="rowAdd"></el-button>
					<el-tooltip v-else content="序号" placement="top"> # </el-tooltip>
				</template>
				<template #default="scope">
					<div :class="['form-table-handle', { 'form-table-handle-delete': !hideDelete }]">
						<span>{{ scope.$index + 1 }}</span>
						<el-button
							v-if="!hideDelete"
							type="danger"
							icon="el-icon-delete"
							size="small"
							plain
							circle
							@click="rowDel(scope.row, scope.$index)"
						></el-button>
					</div>
				</template>
			</el-table-column>
			<el-table-column label="" width="50" v-if="dragSort">
				<template #header>
					<el-icon>
						<el-tooltip content="拖动排序" placement="top">
							<WarningFilled />
						</el-tooltip>
					</el-icon>
				</template>
				<template #default>
					<div class="move" style="cursor: move">
						<el-icon>
							<Sort />
						</el-icon>
					</div>
				</template>
			</el-table-column>
			<slot></slot>
			<template #empty>
				{{ placeholder }}
			</template>
		</el-table>
	</div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, nextTick } from 'vue';
import Sortable from 'sortablejs';
import { WarningFilled, Sort } from '@element-plus/icons-vue';

interface Props {
	/**
	 * 表格数据
	 */
	modelValue?: any[];
	/**
	 * 新增行模板
	 */
	addTemplate?: Record<string, any>;
	/**
	 * 无数据时的提示语
	 */
	placeholder?: string;
	/**
	 * 是否启用拖拽排序
	 */
	dragSort?: boolean;
	/**
	 * 是否隐藏新增按钮
	 */
	hideAdd?: boolean;
	/**
	 * 是否隐藏删除按钮
	 */
	hideDelete?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
	modelValue: () => [],
	addTemplate: () => ({}),
	placeholder: '暂无数据',
	dragSort: false,
	hideAdd: false,
	hideDelete: false,
});

const emit = defineEmits<{
	(e: 'update:modelValue', value: any[]): void;
	(e: 'delete', row: any): void;
}>();

const scFormTable = ref<HTMLElement>();
const table = ref();
const data = ref<any[]>([]);

watch(
	() => props.modelValue,
	() => {
		data.value = props.modelValue;
	},
);

watch(
	data,
	() => {
		/**
		 * 更新表格数据
		 * @event update:modelValue
		 * @type {Array}
		 */
		emit('update:modelValue', data.value);
	},
	{ deep: true },
);

/**
 * 启用表格行拖拽排序
 */
const rowDrop = () => {
	const tbody = table.value.$el.querySelector('.el-table__body-wrapper tbody');
	Sortable.create(tbody, {
		handle: '.move',
		animation: 300,
		ghostClass: 'ghost',
		onEnd({ newIndex, oldIndex }: { newIndex: number; oldIndex: number }) {
			const currRow = data.value.splice(oldIndex, 1)[0];
			data.value.splice(newIndex, 0, currRow);
			const newArray = data.value.slice(0);
			const tmpHeight = scFormTable.value!.offsetHeight;
			scFormTable.value!.style.setProperty('height', tmpHeight + 'px');
			data.value = [];
			nextTick(() => {
				data.value = newArray;
				nextTick(() => {
					scFormTable.value!.style.removeProperty('height');
				});
			});
		},
	});
};

/**
 * 新增一行
 */
const rowAdd = () => {
	const temp = JSON.parse(JSON.stringify(props.addTemplate));
	data.value.push(temp);
};

/**
 * 删除一行
 * @param row - 要删除的行数据
 * @param index - 要删除的行的索引
 */
const rowDel = (row: any, index: number) => {
	data.value.splice(index, 1);
	emit('delete', row);
};

/**
 * 插入一行
 * @param row - 要插入的行数据，默认为新增行模板
 */
const pushRow = (row?: Record<string, any>) => {
	const temp = row || JSON.parse(JSON.stringify(props.addTemplate));
	data.value.push(temp);
};

/**
 * 根据索引删除一行
 * @param index - 要删除的行的索引
 */
const deleteRow = (index: number) => {
	data.value.splice(index, 1);
};

onMounted(() => {
	data.value = props.modelValue;
	if (props.dragSort) {
		rowDrop();
	}
});

defineExpose({
	rowAdd,
	rowDel,
	pushRow,
	deleteRow,
});
</script>

<style scoped>
.form-table {
	width: 100%;
}

.form-table .form-table-handle {
	text-align: center;
}

.form-table .form-table-handle span {
	display: inline-block;
}

.form-table .form-table-handle button {
	display: none;
}

.form-table .hover-row .form-table-handle-delete span {
	display: none;
}

.form-table .hover-row .form-table-handle-delete button {
	display: inline-block;
}

.form-table .move {
	text-align: center;
	font-size: 14px;
	margin-top: 3px;
}
</style>
