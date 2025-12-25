import { defineStore } from 'pinia';
import { useUserInfo } from '/@/stores/userInfo';
import { pageList as userNoticePageList, putObj as updateUserNotice, delObj as deleteUserNotice, deleteAllObj } from '/@/api/admin/user-notice';

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
	senderName?: string;
	senderAvatar?: string;
}

export const useNoticeCenter = defineStore('noticeCenter', {
	state: () => ({
		loading: false,
		items: [] as NoticeCenterItem[],
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

				const records = res?.data?.records ?? [];
				
				this.items = records.map((r: any) => ({
					userNoticeId: r.id,
					noticeId: r.noticeId,
					title: r.title,
					content: r.content,
					type: r.type,
					priority: r.priority,
					isRead: r.isRead,
					createTime: r.createTime,
					readTime: r.readTime,
					senderName: r.senderName,
					senderAvatar: r.senderAvatar,
				}));
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
			await deleteAllObj();
			this.items = [];
		},
	},
});
