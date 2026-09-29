import request from '/@/utils/request';

export const list = (params?: Object) => {
	return request({
		url: '/admin/workflow/definition/list',
		method: 'get',
		params,
	});
};

export const startable = (params?: Object) => {
	return request({
		url: '/admin/workflow/definition/startable',
		method: 'get',
		params,
	});
};

export const getStartForm = (processDefinitionId: string) => {
	return request({
		url: `/admin/workflow/definition/start-form/${encodeURIComponent(processDefinitionId)}`,
		method: 'get',
	});
};

export const deploy = (data: FormData) => {
	return request({
		url: '/admin/workflow/definition/deploy',
		method: 'post',
		data,
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

export const getDiagram = (processDefinitionId: String) => {
	return request({
		url: '/admin/workflow/definition/diagram/' + processDefinitionId,
		method: 'get',
	});
};

export const deployDemo = () => request({ url: '/admin/workflow/definition/deploy-demo', method: 'post' });
