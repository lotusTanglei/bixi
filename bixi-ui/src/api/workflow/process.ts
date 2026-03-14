import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/workflow/process/page',
		method: 'get',
		params,
	});
};

export const myProcessPageList = (params?: Object) => {
	return request({
		url: '/workflow/process/my/page',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/workflow/process/details/' + id,
		method: 'get',
	});
};

export const start = (obj: Object) => {
	return request({
		url: '/workflow/process/start',
		method: 'post',
		data: obj,
	});
};

export const cancel = (id: String) => {
	return request({
		url: '/workflow/process/cancel/' + id,
		method: 'delete',
	});
};

export const suspend = (id: String) => {
	return request({
		url: '/workflow/process/suspend/' + id,
		method: 'put',
	});
};

export const activate = (id: String) => {
	return request({
		url: '/workflow/process/activate/' + id,
		method: 'put',
	});
};

export const getHistory = (processInstanceId: String) => {
	return request({
		url: '/workflow/process/history/' + processInstanceId,
		method: 'get',
	});
};

export const getDiagram = (processInstanceId: String) => {
	return request({
		url: '/workflow/process/diagram/' + processInstanceId,
		method: 'get',
	});
};

export const getForm = (processDefinitionId: String) => {
	return request({
		url: '/workflow/process/form/' + processDefinitionId,
		method: 'get',
	});
};
