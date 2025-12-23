import { defineStore } from 'pinia';
import { useUserInfo } from '/@/stores/userInfo';
import { pageList as userNoticePageList, putObj as updateUserNotice, delObj as deleteUserNotice } from '/@/api/admin/user-notice';
import { getObj as getNotice } from '/@/api/admin/notice';

export interface NoticeCenterItem {
	userNoticeId: number;
	noticeId: number;
	title: string;
	content: string;
	type?: string;
	priority?: string;
	isRead: string;
	createTime?: string;
	readTime?: string;
}

type NoticeEntity = {
	id: number;
	title: string;
	content: string;
	type?: string;
	priority?: string;
	createTime?: string;
};

type UserNoticeEntity = {
	id: number;
	noticeId: number;
	userId: number;
	isRead: string;
	createTime?: string;
	readTime?: string;
};

export const useNoticeCenter = defineStore('noticeCenter', {
	state: () => ({
		loading: false,
		items: [] as NoticeCenterItem[],
		noticeCache: {} as Record<number, NoticeEntity>,
	}),
	getters: {
		unreadItems(state) {
			return state.items.filter((i) => i.isRead !== '1');
		},
		readItems(state) {
			return state.items.filter((i) => i.isRead === '1');
		},
		unreadCount(): number {
			return this.unreadItems.length;
		},
	},
	actions: {
		async refresh(limit = 50) {
			const userInfo = useUserInfo();
			const userId = userInfo.userInfos?.user?.id;
			if (!userId) {
				this.items = [];
				return;
			}

			this.loading = true;
			try {
				const res = await userNoticePageList({
					current: 1,
					size: limit,
					userId,
				});

				const records: UserNoticeEntity[] = res?.data?.records ?? [];
				const noticeIds = Array.from(new Set(records.map((r) => r.noticeId).filter(Boolean)));
				const missingIds = noticeIds.filter((id) => !this.noticeCache[id]);
				if (missingIds.length) {
					const results = await Promise.allSettled(missingIds.map((id) => getNotice(String(id))));
					for (const result of results) {
						if (result.status !== 'fulfilled') continue;
						const notice: NoticeEntity | undefined = result.value?.data;
						if (notice?.id) {
							this.noticeCache[notice.id] = notice;
						}
					}
				}

				this.items = records
					.map((r) => {
						const notice = this.noticeCache[r.noticeId];
						return {
							userNoticeId: r.id,
							noticeId: r.noticeId,
							title: notice?.title ?? '',
							content: notice?.content ?? '',
							type: notice?.type,
							priority: notice?.priority,
							isRead: r.isRead,
							createTime: notice?.createTime ?? r.createTime,
							readTime: r.readTime,
						} as NoticeCenterItem;
					})
					.filter((i) => i.noticeId && i.userNoticeId);
			} finally {
				this.loading = false;
			}
		},

		async markRead(userNoticeId: number) {
			await updateUserNotice({
				id: userNoticeId,
				isRead: '1',
			});
			const item = this.items.find((i) => i.userNoticeId === userNoticeId);
			if (item) item.isRead = '1';
		},

		async markAllRead() {
			const unreadIds = this.items.filter((i) => i.isRead !== '1').map((i) => i.userNoticeId);
			if (!unreadIds.length) return;
			await Promise.all(unreadIds.map((id) => updateUserNotice({ id, isRead: '1' })));
			for (const item of this.items) {
				item.isRead = '1';
			}
		},

		async remove(userNoticeId: number) {
			await deleteUserNotice(String(userNoticeId));
			this.items = this.items.filter((i) => i.userNoticeId !== userNoticeId);
		},

		async removeAll() {
			const ids = this.items.map((i) => i.userNoticeId);
			if (!ids.length) return;
			await Promise.all(ids.map((id) => deleteUserNotice(String(id))));
			this.items = [];
		},
	},
});

