import request from '/@/utils/request';

export interface LeaveForm {
	approverId: string | number;
	startDate: string;
	endDate: string;
	reason: string;
}
export interface LeaveRequest extends LeaveForm {
	id: string;
	applicantId: string;
	leaveStatus: string;
	processInstanceId?: string;
	submittedAt?: string;
	endedAt?: string;
	round: number;
}
const base = '/admin/demo/leave';
export const fetchList = (params?: Record<string, unknown>) => request({ url: `${base}/page`, params });
export const getObj = (id: string) => request({ url: `${base}/details/${id}` });
export const save = (data: LeaveForm, id?: string) => request({ url: id ? `${base}/${id}` : base, method: id ? 'put' : 'post', data });
export const remove = (id: string) => request({ url: `${base}/${id}`, method: 'delete' });
export const submit = (id: string) => request({ url: `${base}/${id}/submit`, method: 'post' });
export const refresh = (id: string) => request({ url: `${base}/${id}/refresh`, method: 'post' });
export const history = (id: string) => request({ url: `${base}/${id}/history` });
export const approvers = (name = '') => request({ url: `${base}/approvers`, params: { name, current: 1, size: 100 } });
export const leaveStatusLabels: Record<string, string> = {
	DRAFT: '草稿', SUBMITTING: '提交处理中', IN_REVIEW: '审批中', APPROVED: '已通过', REJECTED: '已拒绝', CANCELED: '已取消',
};
