<template>
	<div class="layout-navbars-breadcrumb-user-news">
		<div class="head-box">
			<div class="head-box-title">{{ $t('user.newTitle') }}</div>
			<div class="head-box-actions" v-if="noticeCount > 0">
				<div class="head-box-btn" v-if="unreadCount > 0" @click="onAllReadClick">{{ $t('user.newBtn') }}</div>
				<div class="head-box-btn ml10" @click="onAllDeleteClick">{{ $t('user.newDelBtn') }}</div>
			</div>
		</div>
		<div class="content-box">
			<div v-loading="noticeCenter.loading">
				<template v-if="noticeCount > 0">
					<el-tabs v-model="activeTab" class="notice-tabs">
						<el-tab-pane :label="`${$t('user.newUnreadTab')}(${unreadCount})`" name="unread">
							<div class="notice-list">
								<div class="content-box-item" v-for="item in noticeCenter.unreadItems" :key="item.userNoticeId" @click="openNotice(item)">
									<div class="content-box-title">
										<span class="content-box-title-text">{{ item.title }}</span>
										<el-button icon="Delete" link type="primary" @click.stop="onDeleteItem(item.userNoticeId)" />
									</div>
									<div class="content-box-msg">{{ item.content }}</div>
									<div class="content-box-time">{{ item.createTime }}</div>
								</div>
							</div>
						</el-tab-pane>
						<el-tab-pane :label="`${$t('user.newReadTab')}(${readCount})`" name="read">
							<div class="notice-list">
								<div class="content-box-item" v-for="item in noticeCenter.readItems" :key="item.userNoticeId" @click="openNotice(item)">
									<div class="content-box-title">
										<span class="content-box-title-text">{{ item.title }}</span>
										<el-button icon="Delete" link type="primary" @click.stop="onDeleteItem(item.userNoticeId)" />
									</div>
									<div class="content-box-msg">{{ item.content }}</div>
									<div class="content-box-time">{{ item.createTime }}</div>
								</div>
							</div>
						</el-tab-pane>
					</el-tabs>
				</template>
				<el-empty :description="$t('user.newDesc')" v-else></el-empty>
			</div>
		</div>

		<el-dialog v-model="dialogVisible" :title="currentNotice?.title" width="520px">
			<div class="dialog-content" v-html="currentNotice?.content"></div>
		</el-dialog>
	</div>
</template>

<script setup lang="ts" name="layoutBreadcrumbUserNews">
import { useNoticeCenter, type NoticeCenterItem } from '/@/stores/noticeCenter';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';

const noticeCenter = useNoticeCenter();
const message = useMessage();
const messageBox = useMessageBox();
const { t } = useI18n();
const $t = t;

const activeTab = ref<'unread' | 'read'>('unread');
const dialogVisible = ref(false);
const currentNotice = ref<NoticeCenterItem | null>(null);

const unreadCount = computed(() => noticeCenter.unreadCount);
const readCount = computed(() => noticeCenter.readItems.length);
const noticeCount = computed(() => noticeCenter.items.length);

const onAllReadClick = () => {
	messageBox
		.confirm($t('user.newAllReadConfirm'))
		.then(async () => {
			await noticeCenter.markAllRead();
			message.success($t('common.optSuccessText'));
		})
		.catch(() => {});
};

const onAllDeleteClick = () => {
	messageBox
		.confirm($t('user.newAllDeleteConfirm'))
		.then(async () => {
			await noticeCenter.removeAll();
			message.success($t('common.optSuccessText'));
		})
		.catch(() => {});
};

const onDeleteItem = (userNoticeId: number) => {
	messageBox
		.confirm($t('user.newDeleteConfirm'))
		.then(async () => {
			await noticeCenter.remove(userNoticeId);
			message.success($t('common.optSuccessText'));
		})
		.catch(() => {});
};

const openNotice = async (item: NoticeCenterItem) => {
	currentNotice.value = item;
	dialogVisible.value = true;
	if (item.isRead !== '1') {
		try {
			await noticeCenter.markRead(item.userNoticeId);
		} catch {}
	}
};

onMounted(async () => {
	await noticeCenter.refresh();
});
</script>

<style scoped lang="scss">
.layout-navbars-breadcrumb-user-news {
	.head-box {
		display: flex;
		border-bottom: 1px solid var(--el-border-color-lighter);
		box-sizing: border-box;
		color: var(--el-text-color-primary);
		justify-content: space-between;
		height: 35px;
		align-items: center;

		.head-box-actions {
			display: flex;
			align-items: center;
		}

		.head-box-btn {
			color: var(--el-color-primary);
			font-size: 13px;
			cursor: pointer;
			opacity: 0.8;

			&:hover {
				opacity: 1;
			}
		}
	}

	.content-box {
		font-size: 13px;

		.content-box-item {
			padding-top: 12px;
			cursor: pointer;

			&:last-of-type {
				padding-bottom: 12px;
			}

			.content-box-title {
				display: flex;
				align-items: center;
				justify-content: space-between;
				gap: 8px;

				.content-box-title-text {
					font-weight: 500;
					flex: 1;
					min-width: 0;
					overflow: hidden;
					text-overflow: ellipsis;
					white-space: nowrap;
				}
			}

			.content-box-msg {
				color: var(--el-text-color-secondary);
				margin-top: 5px;
				margin-bottom: 5px;
				overflow: hidden;
				text-overflow: ellipsis;
				display: -webkit-box;
				-webkit-line-clamp: 2;
				-webkit-box-orient: vertical;
			}

			.content-box-time {
				color: var(--el-text-color-secondary);
			}
		}
	}

	.notice-list {
		max-height: 360px;
		overflow: auto;
		padding-right: 4px;
	}

	.dialog-content {
		word-break: break-word;
	}

	:deep(.el-empty__description p) {
		font-size: 13px;
	}

	:deep(.notice-tabs .el-tabs__header) {
		margin: 0 0 8px 0;
	}
}
</style>
