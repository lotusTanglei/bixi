import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/script/script-execution-plan/page',
		method: 'get',
		params,
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/script/script-execution-plan',
		method: 'post',
		data: obj,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/script/script-execution-plan/' + id,
		method: 'get',
	});
};

export const delObj = (id: String) => {
	return request({
		url: '/script/script-execution-plan/' + id,
		method: 'delete',
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/script/script-execution-plan',
		method: 'put',
		data: obj,
	});
};
