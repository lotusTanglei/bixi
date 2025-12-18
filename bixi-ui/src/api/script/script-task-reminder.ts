import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/script/script-task-reminder/page',
		method: 'get',
		params,
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/script/script-task-reminder',
		method: 'post',
		data: obj,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/script/script-task-reminder/' + id,
		method: 'get',
	});
};

export const delObj = (id: String) => {
	return request({
		url: '/script/script-task-reminder/' + id,
		method: 'delete',
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/script/script-task-reminder',
		method: 'put',
		data: obj,
	});
};
