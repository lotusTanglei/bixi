import request from '/@/utils/request';

export const chat = (data: object) => {
	return request({
		url: '/ai/chat',
		method: 'post',
		data,
	});
};

export const ragChat = (data: object) => {
	return request({
		url: '/ai/rag',
		method: 'post',
		data,
	});
};

export const streamChat = (data: object) => {
	return request({
		url: '/ai/stream',
		method: 'post',
		data,
		responseType: 'stream',
	});
};

export const sessionList = (params?: object) => {
	return request({
		url: '/ai/session/list',
		method: 'get',
		params,
	});
};

export const createSession = (data: object) => {
	return request({
		url: '/ai/session',
		method: 'post',
		data,
	});
};

export const updateSession = (data: object) => {
	return request({
		url: '/ai/session',
		method: 'put',
		data,
	});
};

export const deleteSession = (id: string) => {
	return request({
		url: '/ai/session/' + id,
		method: 'delete',
	});
};

export const messageList = (sessionId: string) => {
	return request({
		url: '/ai/message/list/' + sessionId,
		method: 'get',
	});
};
