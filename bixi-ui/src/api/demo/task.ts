import request from '/@/utils/request';

export interface DemoTaskForm {
	id?: string;
	title: string;
	assignee: string;
	priority: 'LOW' | 'MEDIUM' | 'HIGH';
	taskStatus: 'TODO' | 'IN_PROGRESS' | 'DONE';
	dueDate: string;
	remark?: string;
}

export function fetchList(query?: Record<string, unknown>) {
	return request({
		url: '/admin/demo/task/page',
		method: 'get',
		params: query,
	});
}

export function getObj(id: string) {
	return request({
		url: `/admin/demo/task/details/${id}`,
		method: 'get',
	});
}

export function addObj(data: DemoTaskForm) {
	return request({
		url: '/admin/demo/task',
		method: 'post',
		data,
	});
}

export function putObj(data: DemoTaskForm) {
	return request({
		url: '/admin/demo/task',
		method: 'put',
		data,
	});
}

export function delObj(ids: string[]) {
	return request({
		url: '/admin/demo/task',
		method: 'delete',
		data: ids,
	});
}
