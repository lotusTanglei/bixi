<template>
	<el-card class="user-card">
		<div class="user-card__header">
			<div class="user-card__identity">
				<el-avatar class="user-card__avatar" shape="circle" :size="60" fit="cover" :src="userData.avatar" />
				<div class="info">
					<span class="user-card__name">{{ userData.name }}</span>
					<span class="user-card__meta">{{ userData.deptName }} | {{ userData.postName }}</span>
				</div>
			</div>
			<span class="user-card__time">
				{{ parseTime(date) }}
			</span>
		</div>
	</el-card>
</template>

<script setup lang="ts" name="currentUser">
import { useUserInfo } from '/@/stores/userInfo';
import { getObj } from '/@/api/admin/user';

const { proxy } = getCurrentInstance();
const date = ref(new Date());

const userData = ref({
	postName: '',
	name: '',
	username: '',
	id: '',
	avatar: '',
	deptName: '',
} as any);
const loading = ref(false);

setInterval(() => {
	date.value = new Date();
}, 1000);

onMounted(() => {
	const data = useUserInfo().userInfos;
	initUserInfo(data.user.id);
});

/**
 * 根据用户 ID 初始化用户信息。
 * @param {any} id - 要查询的用户 ID。
 * @returns {Promise<void>} - 初始化用户信息的 Promise 实例。
 */
const initUserInfo = async (id: any): Promise<void> => {
	try {
		loading.value = true; // 显示加载状态

		const res = await getObj(id); // 执行查询操作
		userData.value = res.data; // 将查询到的数据保存到 userData 变量中
		userData.value.postName = res.data?.postList?.map((item: any) => item.name).join(',') || ''; // 将 postList 中的 postName 合并成字符串并保存到 userData 变量中
		// 文件上传增加后端前缀
		userData.value.avatar = proxy.baseURL + res.data.avatar;
	} finally {
		loading.value = false; // 结束加载状态
	}
};
</script>

<style scoped>
.user-card {
	height: 100%;
	border-top: 3px solid var(--bixi-color-primary);
}

.user-card__header,
.user-card__identity {
	display: flex;
	align-items: center;
}

.user-card__header {
	justify-content: space-between;
}

.user-card__avatar {
	background: var(--bixi-color-primary);
	color: var(--bixi-color-paper);
}

.user-card__name {
	display: block;
	font-size: 18px;
	font-weight: 800;
}

.user-card__meta,
.user-card__time {
	color: var(--bixi-color-muted);
	font-size: 13px;
}

.info {
	margin-left: 8px;
	display: flex;
	flex-direction: column;
	justify-content: center;
}
</style>
