import request from '/@/utils/request';

export const getConfig = () => {
	return request({
		url: '/ai/config',
		method: 'get',
	});
};

export const updateConfig = (data: object) => {
	return request({
		url: '/ai/config',
		method: 'put',
		data,
	});
};

export const getModelList = () => {
	return request({
		url: '/ai/models',
		method: 'get',
	});
};
