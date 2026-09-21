import request from '/@/utils/request';

export const pageList = (params?: Object) => {
	return request({
		url: '/admin/workflow/process/page',
		method: 'get',
		params,
	});
};

export const myProcessPageList = (params?: Object) => {
	return request({
		url: '/admin/workflow/process/my/page',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/admin/workflow/process/details/' + id,
		method: 'get',
	});
};

export interface ProcessStartRequest {
	requestId: string;
	processKey: string;
	title: string;
	remark: string;
	variables: Record<string, unknown>;
}

export const start = (obj: ProcessStartRequest) => {
	return request({
		url: '/admin/workflow/process/start',
		method: 'post',
		data: obj,
	});
};

export const getCommand = (requestId: string) => {
	return request({
		url: '/admin/workflow/command/' + encodeURIComponent(requestId),
		method: 'get',
	});
};

export const cancel = (id: String, requestId: string, reason = '用户取消') => {
	return request({
		url: '/admin/workflow/process/cancel/' + id,
		method: 'delete',
		params: { requestId, reason },
	});
};

export const suspend = (id: String, requestId: string) => {
	return request({
		url: '/admin/workflow/process/suspend/' + id,
		method: 'put',
		params: { requestId },
	});
};

export const activate = (id: String, requestId: string) => {
	return request({
		url: '/admin/workflow/process/activate/' + id,
		method: 'put',
		params: { requestId },
	});
};

export const getHistory = (processInstanceId: String) => {
	return request({
		url: '/admin/workflow/process/history/' + processInstanceId,
		method: 'get',
	});
};

export const getDiagram = (processInstanceId: String) => {
	return request({
		url: '/admin/workflow/process/diagram/' + processInstanceId,
		method: 'get',
	});
};

export const getForm = (processDefinitionId: String) => {
	return request({
		url: '/admin/workflow/process/form/' + processDefinitionId,
		method: 'get',
	});
};
