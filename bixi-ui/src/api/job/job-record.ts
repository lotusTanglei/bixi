import request from '/@/utils/request';

export function fetchList(query: any) {
	return request({
		url: '/job/sys-job-record/page',
		method: 'get',
		params: query,
	});
}

export function delObjs(ids: object) {
	return request({
		url: '/job/sys-job-record',
		method: 'delete',
		data: ids,
	});
}
