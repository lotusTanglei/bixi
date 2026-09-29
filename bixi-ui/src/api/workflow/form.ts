import request from '/@/utils/request';

export const getFormList = (params?: Object) => {
	return request({
		url: '/admin/workflow/form/list',
		method: 'get',
		params,
	});
};

export const getFormByKey = (formKey: string) => {
	return request({
		url: '/admin/workflow/form/' + formKey,
		method: 'get',
	});
};

export const createForm = (data: Object) => {
	return request({
		url: '/admin/workflow/form',
		method: 'post',
		data,
	});
};

export const updateForm = (data: Object) => {
	return request({
		url: '/admin/workflow/form',
		method: 'put',
		data,
	});
};

export const deleteForms = (ids: Array<string | number>) => {
	return request({
		url: '/admin/workflow/form',
		method: 'delete',
		data: ids,
	});
};

export const getFormRender = (formKey: string) => {
	return request({
		url: '/admin/workflow/form/render/' + formKey,
		method: 'get',
	});
};

export const getVersionList = (formId: string | number) => {
	return request({
		url: '/admin/workflow/form/version/list/' + formId,
		method: 'get',
	});
};

export const createVersion = (data: Object) => {
	return request({
		url: '/admin/workflow/form/version',
		method: 'post',
		data,
	});
};

export const activateVersion = (formId: string | number, version: string | number) => {
	return request({
		url: '/admin/workflow/form/version/activate/' + formId + '/' + version,
		method: 'put',
	});
};

export const rollbackVersion = (formId: string | number, version: string | number) => {
	return request({
		url: '/admin/workflow/form/version/rollback/' + formId + '/' + version,
		method: 'post',
	});
};

export const diffVersions = (formId: string | number, v1: string | number, v2: string | number) => {
	return request({
		url: '/admin/workflow/form/version/diff/' + formId + '/' + v1 + '/' + v2,
		method: 'get',
	});
};

export const saveFormData = (data: Object) => {
	return request({
		url: '/admin/workflow/form/data',
		method: 'post',
		data,
	});
};

export const getFormDataByProcess = (processInstanceId: string) => {
	return request({
		url: '/admin/workflow/form/data/process/' + processInstanceId,
		method: 'get',
	});
};

export const getFormDataByTask = (taskId: string) => {
	return request({
		url: '/admin/workflow/form/data/task/' + taskId,
		method: 'get',
	});
};

export const getFieldPermissions = (
	formId: string | number,
	roleId: string | number,
	formVersionId: string | number,
	params?: { processDefinitionId?: string; taskDefinitionKey?: string }
) => {
	return request({
		url: `/admin/workflow/form/permission/field/${formId}/${roleId}/${formVersionId}`,
		method: 'get',
		params,
	});
};

export const saveFieldPermissions = (
	formId: string | number,
	roleId: string | number,
	formVersionId: string | number,
	data: Object
) => {
	return request({
		url: `/admin/workflow/form/permission/field/${formId}/${roleId}/${formVersionId}`,
		method: 'put',
		data,
	});
};
