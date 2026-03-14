import request from '/@/utils/request';

export const todoPageList = (params?: Object) => {
	return request({
		url: '/workflow/task/todo/page',
		method: 'get',
		params,
	});
};

export const donePageList = (params?: Object) => {
	return request({
		url: '/workflow/task/done/page',
		method: 'get',
		params,
	});
};

export const getObj = (id: String) => {
	return request({
		url: '/workflow/task/details/' + id,
		method: 'get',
	});
};

export const complete = (obj: Object) => {
	return request({
		url: '/workflow/task/complete',
		method: 'post',
		data: obj,
	});
};

export const reject = (obj: Object) => {
	return request({
		url: '/workflow/task/reject',
		method: 'post',
		data: obj,
	});
};

export const transfer = (obj: Object) => {
	return request({
		url: '/workflow/task/transfer',
		method: 'post',
		data: obj,
	});
};

export const delegate = (obj: Object) => {
	return request({
		url: '/workflow/task/delegate',
		method: 'post',
		data: obj,
	});
};

export const claim = (taskId: String, userId: String) => {
	return request({
		url: '/workflow/task/claim',
		method: 'post',
		params: {
			taskId,
			userId,
		},
	});
};

export const unclaim = (taskId: String) => {
	return request({
		url: '/workflow/task/unclaim/' + taskId,
		method: 'post',
	});
};

export const getCommentList = (taskId: String) => {
	return request({
		url: '/workflow/task/comment/' + taskId,
		method: 'get',
	});
};

export const addComment = (obj: Object) => {
	return request({
		url: '/workflow/task/comment',
		method: 'post',
		data: obj,
	});
};
