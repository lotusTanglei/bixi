import request from '/@/utils/request';

export const getFormList = (params?: Object) => {
	return request({
		url: '/workflow/form/page',
		method: 'get',
		params,
	});
};

export const getFormByKey = (formKey: String) => {
	return request({
		url: '/workflow/form/key/' + formKey,
		method: 'get',
	});
};

export const createForm = (data: Object) => {
	return request({
		url: '/workflow/form',
		method: 'post',
		data,
	});
};

export const updateForm = (data: Object) => {
	return request({
		url: '/workflow/form',
		method: 'put',
		data,
	});
};

export const deleteForm = (id: String) => {
	return request({
		url: '/workflow/form/' + id,
		method: 'delete',
	});
};

export const getFormRender = (formKey: String) => {
	return request({
		url: '/workflow/form/render/' + formKey,
		method: 'get',
	});
};

export const getVersionList = (formId: String) => {
	return request({
		url: '/workflow/form/version/list/' + formId,
		method: 'get',
	});
};

export const createVersion = (data: Object) => {
	return request({
		url: '/workflow/form/version',
		method: 'post',
		data,
	});
};

export const activateVersion = (formId: String, version: String) => {
	return request({
		url: '/workflow/form/version/activate/' + formId + '/' + version,
		method: 'put',
	});
};

export const rollbackVersion = (formId: String, version: String) => {
	return request({
		url: '/workflow/form/version/rollback/' + formId + '/' + version,
		method: 'put',
	});
};

export const diffVersions = (formId: String, v1: String, v2: String) => {
	return request({
		url: '/workflow/form/version/diff/' + formId + '/' + v1 + '/' + v2,
		method: 'get',
	});
};

export const saveFormData = (data: Object) => {
	return request({
		url: '/workflow/form/data',
		method: 'post',
		data,
	});
};

export const getFormDataByProcess = (processInstanceId: String) => {
	return request({
		url: '/workflow/form/data/process/' + processInstanceId,
		method: 'get',
	});
};

export const getFormDataByTask = (taskId: String) => {
	return request({
		url: '/workflow/form/data/task/' + taskId,
		method: 'get',
	});
};

export const getPermissionList = (formId: String) => {
	return request({
		url: '/workflow/form/permission/list/' + formId,
		method: 'get',
	});
};

export const savePermission = (data: Object) => {
	return request({
		url: '/workflow/form/permission',
		method: 'post',
		data,
	});
};

export const getFieldPermissions = (formId: String, roleId: String) => {
	return request({
		url: '/workflow/form/permission/field/' + formId + '/' + roleId,
		method: 'get',
	});
};
