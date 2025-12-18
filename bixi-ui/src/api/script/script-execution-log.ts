import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/script/script-execution-log/page',
		method: 'get',
		params,
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/script/script-execution-log',
		method: 'post',
		data: obj,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/script/script-execution-log/' + id,
		method: 'get',
	});
};

export const delObj = (id: String) => {
	return request({
		url: '/script/script-execution-log/' + id,
		method: 'delete',
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/script/script-execution-log',
		method: 'put',
		data: obj,
	});
};
