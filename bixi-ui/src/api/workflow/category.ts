import request from '/@/utils/request';

export const list = (params?: Object) => {
	return request({
		url: '/workflow/category/list',
		method: 'get',
		params,
	});
};

export const tree = (params?: Object) => {
	return request({
		url: '/workflow/category/tree',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/workflow/category/details/' + id,
		method: 'get',
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/workflow/category',
		method: 'post',
		data: obj,
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/workflow/category',
		method: 'put',
		data: obj,
	});
};

export const delObj = (ids: Object) => {
	return request({
		url: '/workflow/category',
		method: 'delete',
		data: ids,
	});
};
