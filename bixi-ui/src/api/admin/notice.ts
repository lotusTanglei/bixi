import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/admin/notice/page',
		method: 'get',
		params,
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/admin/notice',
		method: 'post',
		data: obj,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/admin/notice/' + id,
		method: 'get',
	});
};

export const delObj = (id: String) => {
	return request({
		url: '/admin/notice/' + id,
		method: 'delete',
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/admin/notice',
		method: 'put',
		data: obj,
	});
};
