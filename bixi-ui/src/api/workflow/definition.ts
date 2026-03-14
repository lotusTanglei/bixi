import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/workflow/definition/page',
		method: 'get',
		params,
	});
};

export const list = (params?: Object) => {
	return request({
		url: '/workflow/definition/list',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/workflow/definition/details/' + id,
		method: 'get',
	});
};

export const deploy = (obj: Object) => {
	return request({
		url: '/workflow/definition/deploy',
		method: 'post',
		data: obj,
	});
};

export const suspend = (id: String) => {
	return request({
		url: '/workflow/definition/suspend/' + id,
		method: 'put',
	});
};

export const activate = (id: String) => {
	return request({
		url: '/workflow/definition/activate/' + id,
		method: 'put',
	});
};

export const delObj = (ids: Object) => {
	return request({
		url: '/workflow/definition',
		method: 'delete',
		data: ids,
	});
};

export const getXml = (deploymentId: String) => {
	return request({
		url: '/workflow/definition/xml/' + deploymentId,
		method: 'get',
	});
};

export const getDiagram = (processDefinitionId: String) => {
	return request({
		url: '/workflow/definition/diagram/' + processDefinitionId,
		method: 'get',
	});
};
