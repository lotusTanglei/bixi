export async function importOwnedGeneratorTable({ request, prefix, dsName, tableName, marker }) {
	const encodedIdentity = `${encodeURIComponent(dsName)}/${encodeURIComponent(tableName)}`;
	const configPath = `${prefix}/table/config/${encodedIdentity}`;
	const lookup = await request(configPath, { method: 'GET' });
	const lookupAction = `inspect ${tableName}`;
	assertSuccessfulResponse(lookup, lookupAction);
	const existing = lookup.body.data;
	if (existing !== null && existing !== undefined) {
		assertOwnedTable(existing, dsName, tableName, marker);
		return { created: false, table: existing };
	}

	const path = `${prefix}/table/import/${encodedIdentity}`;
	const response = await request(path, { method: 'POST', body: { author: marker } });
	const action = `import ${tableName}`;
	assertSuccessfulResponse(response, action);

	const result = response.body.data;
	if (!result || typeof result.created !== 'boolean' || !result.table) {
		throw new Error(`${action} returned an invalid import result`);
	}
	assertOwnedTable(result.table, dsName, tableName, marker);
	return result;
}

function assertSuccessfulResponse(response, action) {
	if (response.status !== 200) {
		throw new Error(`${action} returned HTTP ${response.status}`);
	}
	if (response.body?.code !== 0) {
		throw new Error(`${action} returned an API error`);
	}
}

function assertOwnedTable(table, dsName, tableName, marker) {
	if (!table.id || table.dsName !== dsName || table.tableName !== tableName) {
		throw new Error(`received the wrong Generator configuration for ${dsName}.${tableName}`);
	}
	if (table.author !== marker) {
		throw new Error(`refusing to use foreign Generator configuration ${dsName}.${tableName}`);
	}
	if (!Array.isArray(table.fieldList) || table.fieldList.length === 0) {
		throw new Error(`Generator configuration has no fields for ${dsName}.${tableName}`);
	}
}
