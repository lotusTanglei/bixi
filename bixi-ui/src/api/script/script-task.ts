import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/script/script-task/page',
		method: 'get',
		params,
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/script/script-task',
		method: 'post',
		data: obj,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/script/script-task/' + id,
		method: 'get',
	});
};

export const delObj = (id: String) => {
	return request({
		url: '/script/script-task/' + id,
		method: 'delete',
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/script/script-task',
		method: 'put',
		data: obj,
	});
};
