import request from '/@/utils/request';
import { consumeSseResponse } from '/@/utils/sse';
import type { AxiosResponse } from 'axios';

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

export const streamChat = async (data: object, onData: (content: string) => void, signal?: AbortSignal) => {
	let response: AxiosResponse<ReadableStream<Uint8Array>>;
	try {
		response = await request<ReadableStream<Uint8Array>, AxiosResponse<ReadableStream<Uint8Array>>>({
			url: '/ai/stream/chat',
			method: 'get',
			params: data,
			headers: { Accept: 'text/event-stream' },
			responseType: 'stream',
			adapter: 'fetch',
			signal,
		});
	} catch (failure: any) {
		if (typeof failure?.data?.getReader !== 'function') throw failure;
		response = failure;
	}
	await consumeSseResponse(response, onData);
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
