import { RouteRecordRaw } from 'vue-router';

const workflowRoutes: Array<RouteRecordRaw> = [
	{
		path: '/workflow',
		name: 'workflow',
		redirect: '/workflow/task/todo',
		meta: {
			title: '工作流管理',
			icon: 'iconfont icon-liucheng',
		},
		children: [
			{
				path: '/workflow/definition',
				name: 'workflowDefinition',
				component: () => import('/@/views/workflow/definition/index.vue'),
				meta: {
					title: '流程定义',
					icon: 'iconfont icon-dingyi',
					isKeepAlive: true,
				},
			},
			{
				path: '/workflow/task',
				name: 'workflowTask',
				redirect: '/workflow/task/todo',
				meta: {
					title: '任务管理',
					icon: 'iconfont icon-renwu',
				},
				children: [
					{
						path: '/workflow/task/todo',
						name: 'workflowTaskTodo',
						component: () => import('/@/views/workflow/task/todo.vue'),
						meta: {
							title: '待办任务',
							icon: 'iconfont icon-daiban',
							isKeepAlive: true,
						},
					},
					{
						path: '/workflow/task/done',
						name: 'workflowTaskDone',
						component: () => import('/@/views/workflow/task/done.vue'),
						meta: {
							title: '已办任务',
							icon: 'iconfont icon-yiban',
							isKeepAlive: true,
						},
					},
				],
			},
			{
				path: '/workflow/process',
				name: 'workflowProcess',
				redirect: '/workflow/process/start',
				meta: {
					title: '流程实例',
					icon: 'iconfont icon-shili',
				},
				children: [
					{
						path: '/workflow/process/start',
						name: 'workflowProcessStart',
						component: () => import('/@/views/workflow/process/start.vue'),
						meta: {
							title: '发起流程',
							icon: 'iconfont icon-faqi',
							isKeepAlive: true,
						},
					},
					{
						path: '/workflow/process/instance',
						name: 'workflowProcessInstance',
						component: () => import('/@/views/workflow/process/instance.vue'),
						meta: {
							title: '我的流程',
							icon: 'iconfont icon-wode',
							isKeepAlive: true,
						},
					},
				],
			},
		],
	},
];

export default workflowRoutes;
