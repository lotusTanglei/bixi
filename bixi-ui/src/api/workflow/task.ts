import request from '/@/utils/request';

export interface WorkflowTaskRow {
	taskId: string;
	taskName?: string;
	processName?: string;
	processInstanceId?: string;
	createTime?: string;
	endTime?: string;
	assignee?: string | null;
	delegationState?: string | null;
	candidateUsers?: string[];
	candidateGroups?: string[];
	claimable?: boolean;
}

export const resolve = (data: { taskId: string; comment: string; requestId: string }) =>
	request({ url: '/admin/workflow/task/resolve', method: 'post', data });

export const todoPageList = (params?: Object) => {
	return request({
		url: '/admin/workflow/task/todo/page',
		method: 'get',
		params,
	});
};

export const donePageList = (params?: Object) => {
	return request({
		url: '/admin/workflow/task/done/page',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/admin/workflow/task/details/' + id,
		method: 'get',
	});
};

export const complete = (obj: Object) => {
	return request({
		url: '/admin/workflow/task/complete',
		method: 'post',
		data: obj,
	});
};

export const reject = (obj: Object) => {
	return request({
		url: '/admin/workflow/task/reject',
		method: 'post',
		data: obj,
	});
};

export const transfer = (obj: Object) => {
	return request({
		url: '/admin/workflow/task/transfer',
		method: 'post',
		data: obj,
	});
};

export const delegate = (obj: Object) => {
	return request({
		url: '/admin/workflow/task/delegate',
		method: 'post',
		data: obj,
	});
};

export const claim = (taskId: String, requestId: string) => {
	return request({
		url: '/admin/workflow/task/claim',
		method: 'post',
		params: {
			taskId,
			requestId,
		},
	});
};

export const unclaim = (taskId: String, requestId: string) => {
	return request({
		url: '/admin/workflow/task/unclaim/' + taskId,
		method: 'post',
		params: { requestId },
	});
};

export const getCommentList = (taskId: String) => {
	return request({
		url: '/admin/workflow/task/comment/' + taskId,
		method: 'get',
	});
};

export const addComment = (obj: Object) => {
	return request({
		url: '/admin/workflow/task/comment',
		method: 'post',
		data: obj,
	});
};
