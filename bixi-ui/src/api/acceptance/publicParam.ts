import request from '/@/utils/request';

export interface SysPublicParamForm {
	id?: string;
	name?: string;
	key?: string;
	value?: string;
	validateCode?: string;
	type?: string;
	systemFlag?: string;
	sn?: number;
	status?: string;
	dataStatus?: string;
	remark?: string;
}

export interface SysPublicParamImportRowError {
	rowNumber: number;
	errors: string[];
}

export interface SysPublicParamImportResult {
	success: boolean;
	code: string;
	totalRows: number;
	importedRows: number;
	errors: SysPublicParamImportRowError[];
}

const baseUrl = '/admin/publicParam';

export const fetchList = (query?: Record<string, unknown>) => request({ url: baseUrl + '/page', method: 'get', params: query });
export const getObj = (id: string) => request({ url: baseUrl + '/details/' + id, method: 'get' });
export const addObj = (data: SysPublicParamForm) => request({ url: baseUrl, method: 'post', data });
export const putObj = (data: SysPublicParamForm) => request({ url: baseUrl, method: 'put', data });
export const delObj = (ids: string[]) => request({ url: baseUrl, method: 'delete', data: ids });
export const importRows = (file: File) => {
	const data = new FormData();
	data.append('file', file);
	return request<SysPublicParamImportResult>({ url: baseUrl + '/import', method: 'post', data });
};
export const exportRows = (query?: Record<string, unknown>) => request({
	url: baseUrl + '/export', method: 'get', params: query, responseType: 'blob',
});
