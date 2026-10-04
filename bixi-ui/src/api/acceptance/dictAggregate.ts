import request from '/@/utils/request';

export interface SysDictItemForm {
	id?: string;
	dictId?: number;
	value?: string;
	label?: string;
	dictType?: string;
	description?: string;
	sn?:  number;
	status?: string;
	dataStatus?: string;
	remark?: string;
}

export interface SysDictForm {
	id?: string;
	type?: string;
	name?: string;
	description?: string;
	sn?:  number;
	systemFlag?: string;
	status?: string;
	dataStatus?: string;
	remark?: string;
	children: SysDictItemForm[];
}

export interface SysDictImportRowError {
	rowNumber: number;
	errors: string[];
}

export interface SysDictImportResult {
	success: boolean;
	code: string;
	totalRows: number;
	importedRows: number;
	errors: SysDictImportRowError[];
}

const baseUrl = '/acceptance/dictAggregate';

export const fetchList = (query?: Record<string, unknown>) => request({ url: baseUrl + '/page', method: 'get', params: query });
export const getObj = (id: string) => request({ url: baseUrl + '/details/' + id, method: 'get' });
export const addObj = (data: SysDictForm) => request({ url: baseUrl, method: 'post', data });
export const putObj = (data: SysDictForm) => request({ url: baseUrl, method: 'put', data });
export const delObj = (ids: string[]) => request({ url: baseUrl, method: 'delete', data: ids });
export const importRows = (file: File) => {
	const data = new FormData();
	data.append('file', file);
	return request<SysDictImportResult>({ url: baseUrl + '/import', method: 'post', data });
};
export const exportRows = (query?: Record<string, unknown>) => request({
	url: baseUrl + '/export', method: 'get', params: query, responseType: 'blob',
});
