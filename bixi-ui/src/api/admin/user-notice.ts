import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/admin/user-notice/page',
		method: 'get',
		params,
	});
};

export const recordPageList = (params?: Object) => {
	return request({
		url: '/admin/user-notice/record/page',
		method: 'get',
		params,
	});
};

export const addObj = (obj: Object) => {
	return request({
		url: '/admin/user-notice',
		method: 'post',
		data: obj,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/admin/user-notice/' + id,
		method: 'get',
	});
};

export const delObj = (id: String) => {
	return request({
		url: '/admin/user-notice/' + id,
		method: 'delete',
	});
};

export const deleteAllObj = () => {
	return request({
		url: '/admin/user-notice/delete/all',
		method: 'delete',
	});
};

export const putObj = (obj: Object) => {
	return request({
		url: '/admin/user-notice',
		method: 'put',
		data: obj,
	});
};
