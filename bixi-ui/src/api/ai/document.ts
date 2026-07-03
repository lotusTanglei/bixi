import request from '/@/utils/request';

export interface AiDocument {
	id: string;
	title: string;
	content?: string;
	source?: string;
	docType?: string;
	vectorStatus?: string | number;
	createTime?: string;
}

export const pageList = (params?: object) => {
	return request({
		url: '/ai/documents/page',
		method: 'get',
		params,
	});
};

export const documentList = (params?: object) => {
	return request({
		url: '/ai/documents/list',
		method: 'get',
		params,
	});
};

export const addDocument = (data: object) => {
	return request({
		url: '/ai/documents',
		method: 'post',
		data,
	});
};

export const uploadDocument = (file: File) => {
	const formData = new FormData();
	formData.append('file', file);
	return request({
		url: '/ai/documents/upload',
		method: 'post',
		data: formData,
		headers: {
			'Content-Type': 'multipart/form-data',
		},
	});
};

export const deleteDocument = (id: string) => {
	return request({
		url: '/ai/documents/' + id,
		method: 'delete',
	});
};

export const searchDocuments = (data: object) => {
	return request({
		url: '/ai/search',
		method: 'post',
		data,
	});
};
