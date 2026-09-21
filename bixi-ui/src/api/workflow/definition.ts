import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/admin/workflow/definition/page',
		method: 'get',
		params,
	});
};

export const list = (params?: Object) => {
	return request({
		url: '/admin/workflow/definition/list',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/admin/workflow/definition/details/' + id,
		method: 'get',
	});
};

export const deploy = (obj: Object) => {
	return request({
		url: '/admin/workflow/definition/deploy',
		method: 'post',
		data: obj,
	});
};

export const suspend = (id: String) => {
	return request({
		url: '/admin/workflow/definition/suspend/' + id,
		method: 'put',
	});
};

export const activate = (id: String) => {
	return request({
		url: '/admin/workflow/definition/activate/' + id,
		method: 'put',
	});
};

export const delObj = (ids: Object) => {
	return request({
		url: '/admin/workflow/definition',
		method: 'delete',
		data: ids,
	});
};

export const getXml = (deploymentId: String) => {
	return request({
		url: '/admin/workflow/definition/xml/' + deploymentId,
		method: 'get',
	});
};

export const getDiagram = (processDefinitionId: String) => {
	return request({
		url: '/admin/workflow/definition/diagram/' + processDefinitionId,
		method: 'get',
	});
};

export const deployDemo = () => request({ url: '/admin/workflow/definition/deploy-demo', method: 'post' });
