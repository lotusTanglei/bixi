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
							<div class="notice-list" v-bind="unreadContainerProps" style="height: 360px; overflow: auto; padding-right: 4px;">
								<div v-bind="unreadWrapperProps">
									<div class="content-box-item" v-for="{ data: item, index } in unreadVirtualList" :key="item.userNoticeId" @click="openNotice(item)">
										<div class="content-box-title">
											<div class="content-box-title-header">
												<el-avatar :size="30" :src="item.senderAvatar" v-if="item.senderAvatar" class="mr10" />
												<el-avatar :size="30" class="mr10" v-else>{{ item.senderName?.charAt(0) }}</el-avatar>
												<div class="header-info">
													<div class="title-row">
														<span class="content-box-title-text">{{ item.title }}</span>
														<el-tag size="small" v-if="item.type === '0'" class="ml5">通知</el-tag>
														<el-tag size="small" type="warning" v-else-if="item.type === '1'" class="ml5">公告</el-tag>
														<el-tag size="small" type="info" v-else class="ml5">私信</el-tag>

														<el-tag size="small" type="info" v-if="item.priority === '0'" class="ml5">普通</el-tag>
														<el-tag size="small" type="warning" v-else-if="item.priority === '1'" class="ml5">重要</el-tag>
														<el-tag size="small" type="danger" v-else class="ml5">紧急</el-tag>
													</div>
													<div class="sender-name">{{ item.senderName }}</div>
												</div>
											</div>
											<el-button icon="Delete" link type="primary" @click.stop="onDeleteItem(item.userNoticeId)" />
										</div>
										<div class="content-box-msg">{{ item.content }}</div>
										<div class="content-box-time">{{ item.createTime }}</div>
									</div>
								</div>
							</div>
						</el-tab-pane>
						<el-tab-pane :label="`${$t('user.newReadTab')}(${readCount})`" name="read">
							<div class="notice-list" v-bind="readContainerProps" style="height: 360px; overflow: auto; padding-right: 4px;">
								<div v-bind="readWrapperProps">
									<div class="content-box-item" v-for="{ data: item, index } in readVirtualList" :key="item.userNoticeId" @click="openNotice(item)">
										<div class="content-box-title">
											<div class="content-box-title-header">
												<el-avatar :size="30" :src="item.senderAvatar" v-if="item.senderAvatar" class="mr10" />
												<el-avatar :size="30" class="mr10" v-else>{{ item.senderName?.charAt(0) }}</el-avatar>
												<div class="header-info">
													<div class="title-row">
														<span class="content-box-title-text">{{ item.title }}</span>
														<el-tag size="small" v-if="item.type === '0'" class="ml5">通知</el-tag>
														<el-tag size="small" type="warning" v-else-if="item.type === '1'" class="ml5">公告</el-tag>
														<el-tag size="small" type="info" v-else class="ml5">私信</el-tag>

														<el-tag size="small" type="info" v-if="item.priority === '0'" class="ml5">普通</el-tag>
														<el-tag size="small" type="warning" v-else-if="item.priority === '1'" class="ml5">重要</el-tag>
														<el-tag size="small" type="danger" v-else class="ml5">紧急</el-tag>
													</div>
													<div class="sender-name">{{ item.senderName }}</div>
												</div>
											</div>
											<el-button icon="Delete" link type="primary" @click.stop="onDeleteItem(item.userNoticeId)" />
										</div>
										<div class="content-box-msg">{{ item.content }}</div>
										<div class="content-box-time">{{ item.createTime }}</div>
									</div>
								</div>
							</div>
						</el-tab-pane>
					</el-tabs>
				</template>
				<el-empty :description="$t('user.newDesc')" v-else></el-empty>
			</div>
		</div>

		<el-dialog v-model="dialogVisible" :title="currentNotice?.title" width="520px">
			<div class="dialog-content" v-html="DOMPurify.sanitize(currentNotice?.content || '')"></div>
		</el-dialog>
	</div>
</template>

<script setup lang="ts" name="layoutBreadcrumbUserNews">
import { ref, computed, onMounted } from 'vue';
import { useNoticeCenter, type NoticeCenterItem } from '/@/stores/noticeCenter';
import { useMessage, useMessageBox } from '/@/hooks/message';
import { useI18n } from 'vue-i18n';
import DOMPurify from 'dompurify';
import { useVirtualList } from '@vueuse/core';

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

const { list: unreadVirtualList, containerProps: unreadContainerProps, wrapperProps: unreadWrapperProps } = useVirtualList(
        computed(() => noticeCenter.unreadItems),
        { itemHeight: 90 }
);

const { list: readVirtualList, containerProps: readContainerProps, wrapperProps: readWrapperProps } = useVirtualList(
        computed(() => noticeCenter.readItems),
        { itemHeight: 90 }
);

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

				.content-box-title-header {
					display: flex;
					align-items: center;
					flex: 1;

					.header-info {
						display: flex;
						flex-direction: column;
						justify-content: center;

						.title-row {
							display: flex;
							align-items: center;

							.content-box-title-text {
								margin-right: 5px;
								font-weight: bold;
							}
						}

						.sender-name {
							font-size: 12px;
							color: var(--el-text-color-secondary);
							margin-top: 2px;
						}
					}
				}
			}

			.content-box-msg {
				color: var(--el-text-color-secondary);
				margin-top: 5px;
				margin-bottom: 5px;
				margin-left: 40px;
			}

			.content-box-time {
				color: var(--el-text-color-secondary);
				margin-left: 40px;
			}
		}
	}

	.notice-list {
		height: 360px;
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
