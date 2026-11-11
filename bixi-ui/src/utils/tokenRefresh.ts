export const refreshAtFrom = (expiresIn: unknown, now = Date.now()) => {
	const seconds = Number(expiresIn);
	if (!Number.isFinite(seconds) || seconds <= 0) throw new Error('Invalid expires_in');
	const lifetime = seconds * 1000;
	return now + lifetime - Math.min(30 * 60 * 1000, lifetime * 0.1);
};

export const isRejectedRefreshCredential = (error: any) =>
	['invalid_grant', 'invalid_client'].includes(error?.msg || error?.error);

let pendingRefresh: Promise<any> | undefined;

export const refreshOnce = <T>(refresh: () => Promise<T>): Promise<T> => {
	if (!pendingRefresh) {
		pendingRefresh = refresh().finally(() => {
			pendingRefresh = undefined;
		});
	}
	return pendingRefresh;
};
