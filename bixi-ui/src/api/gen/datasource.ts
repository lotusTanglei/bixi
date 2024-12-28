import request from '/@/utils/request';

export function fetchList(query?: Object) {
	return request({
		url: '/gen/dsconfig/page',
		method: 'get',
		params: query,
	});
}

export function list(query?: Object) {
	return request({
		url: '/gen/dsconfig/list',
		method: 'get',
		params: query,
	});
}

export function listTable(query?: Object) {
	return request({
		url: '/gen/dsconfig/table/list',
		method: 'get',
		params: query,
	});
}

export function addObj(obj?: Object) {
	return request({
		url: '/gen/dsconfig',
		method: 'post',
		data: obj,
	});
}

export function getObj(id?: string) {
	return request({
		url: '/gen/dsconfig/' + id,
		method: 'get',
	});
}

export function delObj(ids?: Object) {
	return request({
		url: '/gen/dsconfig',
		method: 'delete',
		data: ids,
	});
}

export function putObj(obj?: Object) {
	return request({
		url: '/gen/dsconfig',
		method: 'put',
		data: obj,
	});
}
