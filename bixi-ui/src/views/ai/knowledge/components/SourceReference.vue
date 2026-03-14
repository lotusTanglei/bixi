<template>
	<div class="source-reference" v-if="sources.length > 0">
		<div class="reference-header" @click="expanded = !expanded">
			<el-icon><Document /></el-icon>
			<span>引用来源 ({{ sources.length }})</span>
			<el-icon class="expand-icon" :class="{ expanded }"><ArrowDown /></el-icon>
		</div>
		<el-collapse-transition>
			<div v-show="expanded" class="reference-list">
				<div
					v-for="(source, index) in sources"
					:key="index"
					class="reference-item"
				>
					<div class="reference-title">
						<el-icon><Document /></el-icon>
						<span>{{ source.documentName }}</span>
						<el-tag size="small" type="info">{{ (source.score * 100).toFixed(1) }}%</el-tag>
					</div>
					<div class="reference-content">
						{{ truncateContent(source.content) }}
					</div>
				</div>
			</div>
		</el-collapse-transition>
	</div>
</template>

<script lang="ts" setup>
import { Document, ArrowDown } from '@element-plus/icons-vue';

interface Source {
	documentId: string;
	documentName: string;
	content: string;
	score: number;
}

defineProps<{
	sources: Source[];
}>();

const expanded = ref(false);

const truncateContent = (content: string, maxLength: number = 200) => {
	if (content.length <= maxLength) return content;
	return content.substring(0, maxLength) + '...';
};
</script>

<style lang="scss" scoped>
.source-reference {
	margin-top: 12px;
	border: 1px solid #e4e7ed;
	border-radius: 8px;
	overflow: hidden;
}

.reference-header {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 10px 12px;
	background-color: #f5f7fa;
	cursor: pointer;
	font-size: 13px;
	color: #606266;

	&:hover {
		background-color: #ecf5ff;
	}

	.expand-icon {
		margin-left: auto;
		transition: transform 0.3s;

		&.expanded {
			transform: rotate(180deg);
		}
	}
}

.reference-list {
	padding: 12px;
	background-color: #fff;
}

.reference-item {
	padding: 10px;
	margin-bottom: 8px;
	background-color: #fafafa;
	border-radius: 6px;

	&:last-child {
		margin-bottom: 0;
	}
}

.reference-title {
	display: flex;
	align-items: center;
	gap: 6px;
	font-size: 13px;
	font-weight: 500;
	color: #303133;
	margin-bottom: 6px;

	.el-tag {
		margin-left: auto;
	}
}

.reference-content {
	font-size: 12px;
	color: #606266;
	line-height: 1.5;
	background-color: #fff;
	padding: 8px;
	border-radius: 4px;
	border-left: 3px solid #409eff;
}
</style>
