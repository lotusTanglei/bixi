interface SseResponse {
	status: number;
	data?: ReadableStream<Uint8Array>;
}

const errorMessage = (data: string, fallback: string) => {
	try {
		const payload = JSON.parse(data);
		return payload.msg || payload.message || fallback;
	} catch {
		return data.trim() || fallback;
	}
};

export const consumeSseResponse = async (response: SseResponse, onData: (data: string) => void) => {
	if (!response.data) throw new Error('流式响应为空');
	if (response.status < 200 || response.status >= 300) {
		throw new Error(errorMessage(await new Response(response.data).text(), `流式请求失败 (${response.status})`));
	}

	const reader = response.data.getReader();
	const decoder = new TextDecoder();
	let pending = '';

	const dispatch = (rawEvent: string) => {
		let event = 'message';
		const data: string[] = [];
		for (const line of rawEvent.split(/\r?\n/)) {
			if (line.startsWith('event:')) event = line.slice(6).trim();
			if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''));
		}
		const content = data.join('\n');
		if (event === 'error') throw new Error(errorMessage(content, 'AI 流式对话失败'));
		if (content && content !== '[DONE]') onData(content);
	};

	try {
		while (true) {
			const { done, value } = await reader.read();
			pending += decoder.decode(value, { stream: !done });
			let boundary = pending.match(/\r?\n\r?\n/);
			while (boundary?.index !== undefined) {
				dispatch(pending.slice(0, boundary.index));
				pending = pending.slice(boundary.index + boundary[0].length);
				boundary = pending.match(/\r?\n\r?\n/);
			}
			if (done) {
				if (pending.trim()) dispatch(pending);
				return;
			}
		}
	} catch (error) {
		await reader.cancel(error).catch(() => undefined);
		throw error;
	} finally {
		reader.releaseLock();
	}
};
