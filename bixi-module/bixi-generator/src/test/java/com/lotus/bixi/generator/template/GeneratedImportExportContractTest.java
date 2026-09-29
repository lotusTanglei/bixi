package com.lotus.bixi.generator.template;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.log.aspect.SysLogAspect;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.common.log.event.SysLogEvent;
import com.lotus.bixi.common.log.event.SysLogEventSource;
import com.lotus.bixi.common.log.event.SysLogListener;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.service.GenFieldTypeService;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.service.GenTableService;
import com.lotus.bixi.generator.service.impl.GeneratorServiceImpl;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.api.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.PrePostTemplateDefaults;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeneratedImportExportContractTest {

	@TempDir
	Path projectRoot;

	@Test
	void rendersIndependentBoundedImportAndMaskedExportContracts() {
		List<Map<String, String>> preview = generator().preview(1L);

		assertThat(preview).hasSize(17);
		assertThat(preview).extracting(entry -> entry.get("codePath"))
			.anyMatch(path -> path.endsWith("/InventoryItemImportDTO.java"))
			.anyMatch(path -> path.endsWith("/InventoryItemExportVO.java"))
			.anyMatch(path -> path.endsWith("/InventoryItemImportResult.java"))
			.anyMatch(path -> path.endsWith("/InventoryItemImportRowError.java"));

		String controller = codeEndingWith(preview, "/InventoryItemController.java");
		assertThat(controller)
			.contains("@HasPermission(\"inventory_inventory_item_import\")")
			.contains("@HasPermission(\"inventory_inventory_item_export\")")
			.contains("@SysLog(\"导入库存物料\")")
			.contains("@SysLog(\"导出库存物料\")")
			.contains("MAX_UPLOAD_BYTES")
			.contains("MAX_IMPORT_ROWS")
			.contains("MultipartFile")
			.contains("@RequestPart(value = \"file\", required = false)")
			.contains("@ResponseExcel")
			.doesNotContain("List<InventoryItem> export");

		String service = codeEndingWith(preview, "/InventoryItemServiceImpl.java");
		assertThat(service)
			.contains("@Transactional(rollbackFor = Exception.class)")
			.contains("InventoryItemImportResult importRows")
			.contains("validator.validate")
			.contains("MAX_IMPORT_ROWS")
			.contains("MAX_IMPORT_ERRORS")
			.contains("DuplicateKeyException")
			.contains("fingerprint")
			.contains("maskEmail")
			.contains("maskPhone")
			.contains("maskIdentity")
			.doesNotContain("setApiToken", "setAccessKey", "setEncryptionKey", "setSigningKey",
				"setSaltValue", "setPwdHash", "setPrivateKey", "setApiKey", "setUserPassword",
				"setPasswdHash", "setRefreshToken", "setClientSecret", "setLoginCredential",
				"setCredentialsBlob");

		String importDto = codeEndingWith(preview, "/InventoryItemImportDTO.java");
		assertThat(importDto)
			.contains("@Size(max = 512")
			.contains("@NotBlank")
			.contains("itemName", "itemCode", "description", "monkey", "donkey", "email", "phone",
				"mobile", "idCard", "identity")
			.doesNotContain("apiToken", "accessKey", "encryptionKey", "signingKey", "saltValue",
				"pwdHash", "privateKey", "apiKey", "userPassword", "passwdHash", "refreshToken",
				"clientSecret", "loginCredential", "credentialsBlob");

		String exportVo = codeEndingWith(preview, "/InventoryItemExportVO.java");
		assertThat(exportVo)
			.contains("@ExcelProperty")
			.contains("itemName", "itemCode", "description", "monkey", "donkey", "email", "phone",
				"mobile", "idCard", "identity")
			.doesNotContain("apiToken", "accessKey", "encryptionKey", "signingKey", "saltValue",
				"pwdHash", "privateKey", "apiKey", "userPassword", "passwdHash", "refreshToken",
				"clientSecret", "loginCredential", "credentialsBlob")
			.doesNotContain("extends BaseEntity");

		String result = codeEndingWith(preview, "/InventoryItemImportResult.java");
		assertThat(result).contains("MAX_ERRORS = 100").contains("List.copyOf");
		String rowError = codeEndingWith(preview, "/InventoryItemImportRowError.java");
		assertThat(rowError).contains("rowNumber").contains("errors").contains("MAX_MESSAGES");

		assertThat(codeEndingWith(preview, "/inventoryItem.ts"))
			.contains("export const importRows")
			.contains("export const exportRows")
			.contains("responseType: 'blob'")
			.contains("InventoryItemImportResult");
		assertThat(codeEndingWith(preview, "/inventoryItem/index.vue"))
			.contains("v-auth=\"'inventory_inventory_item_import'\"")
			.contains("v-auth=\"'inventory_inventory_item_export'\"")
			.contains("importResult.errors")
			.contains("错误行");

		assertThat(codeEndingWith(preview, "/inventory_inventoryItem_menu.sql"))
			.contains("'inventory_inventory_item_import'", "'inventory_inventory_item_export'");
	}

	@Test
	void generatedImportServiceExecutesValidationDuplicateRollbackBoundsAndMaskedExport() throws Exception {
		List<Map<String, String>> preview = generator().preview(1L);
		Class<?> serviceType = compileAndLoad(preview,
			"com.lotus.bixi.inventory.service.impl.InventoryItemServiceImpl");
		GeneratedImportRuntime runtime = new GeneratedImportRuntime(serviceType);

		Object success = runtime.importRows(List.of(
			runtime.row("Alpha", "alice@example.com", "13812345678", "110101199001013456", 1),
			runtime.row("Beta", "bob@example.com", "13912345678", "110101199101013457", 2)));
		assertThat(runtime.success(success)).isTrue();
		assertThat(runtime.importedRows(success)).isEqualTo(2);
		assertThat(runtime.store()).hasSize(2);

		List<?> exported = runtime.exportRows();
		assertThat(exported).hasSize(2);
		assertThat(runtime.property(exported.get(0), "ItemName")).isEqualTo("Alpha");
		assertThat(runtime.property(exported.get(0), "Email")).isEqualTo("a***@example.com");
		assertThat(runtime.property(exported.get(0), "Phone")).isEqualTo("138****5678");
		assertThat(runtime.property(exported.get(0), "IdCard")).isEqualTo("11********3456");
		assertThat(runtime.property(exported.get(0), "Quantity")).isEqualTo(1);
		assertThat(exported.get(0).getClass().getMethods())
			.noneMatch(method -> method.getName().equals("getApiToken"));

		runtime.clear();
		Object invalid = runtime.importRows(List.of(
			runtime.row(" ", "one@example.com", "13812345678", "110101199001013456", 1),
			runtime.row("Valid", "two@example.com", "13912345678", "110101199101013457", null)));
		assertThat(runtime.success(invalid)).isFalse();
		assertThat(runtime.code(invalid)).isEqualTo("VALIDATION_FAILED");
		assertThat(runtime.errorRows(invalid)).contains(2, 3);
		assertThat(runtime.store()).isEmpty();

		Object oversizedCell = runtime.importRows(List.of(
			runtime.row("X".repeat(513), "long@example.com", "13812345678", "110101199001013456", 1)));
		assertThat(runtime.success(oversizedCell)).isFalse();
		assertThat(runtime.code(oversizedCell)).isEqualTo("VALIDATION_FAILED");
		assertThat(runtime.errorMessages(oversizedCell)).contains("物料名称长度不能超过512");
		assertThat(runtime.store()).isEmpty();

		List<Object> tooManyRows = new ArrayList<>();
		for (int index = 0; index < 1001; index++) {
			tooManyRows.add(runtime.row("Item-" + index, "user" + index + "@example.com",
				"13812345678", "110101199001013456", index));
		}
		Object rowLimit = runtime.importRows(tooManyRows);
		assertThat(runtime.success(rowLimit)).isFalse();
		assertThat(runtime.code(rowLimit)).isEqualTo("ROW_LIMIT_EXCEEDED");
		assertThat(runtime.errorRows(rowLimit)).containsExactly(1001);
		assertThat(runtime.store()).isEmpty();

		Object boundedMessages = runtime.rowError(2,
			List.of("one", "two", "three", "four", "five", "six", "seven", "eight", "nine"));
		assertThat(runtime.rowErrorMessages(boundedMessages)).hasSize(8)
			.doesNotContain("nine");
		Object boundedMessageLength = runtime.rowError(2,
			List.of("line one\r\nline two\n" + "X".repeat(10_000)));
		String normalizedMessage = runtime.rowErrorMessages(boundedMessageLength).get(0);
		assertThat(normalizedMessage.codePointCount(0, normalizedMessage.length())).isLessThanOrEqualTo(256);
		assertThat(normalizedMessage).doesNotContain("\r", "\n");
		assertThatThrownBy(() -> runtime.rowError(1002, List.of("outside bounds")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("导入错误行号超出范围");

		Object duplicate = runtime.importRows(List.of(
			runtime.row("  ALPHA  ", "same@example.com", "13812345678", "110101199001013456", 1),
			runtime.row("alpha", "same@example.com", "13812345678", "110101199001013456", 1)));
		assertThat(runtime.success(duplicate)).isFalse();
		assertThat(runtime.errorMessages(duplicate)).contains("上传文件内存在重复数据行");
		assertThat(runtime.store()).isEmpty();

		runtime.failDuplicate("DB-DUP");
		Object databaseDuplicate = runtime.importRows(List.of(
			runtime.row("DB-DUP", "hidden@example.com", "13812345678", "110101199001013456", 1)));
		assertThat(runtime.success(databaseDuplicate)).isFalse();
		assertThat(runtime.code(databaseDuplicate)).isEqualTo("DUPLICATE_KEY");
		assertThat(runtime.errorMessages(databaseDuplicate)).containsExactly("数据唯一性冲突");
		assertThat(runtime.errorMessages(databaseDuplicate)).noneMatch(message -> message.contains("DB-DUP"));
		assertThat(runtime.store()).isEmpty();

		runtime.failNthInsert(2);
		Object writeFailure = runtime.importRows(List.of(
			runtime.row("First", "one@example.com", "13812345678", "110101199001013456", 1),
			runtime.row("Second", "two@example.com", "13912345678", "110101199101013457", 2)));
		assertThat(runtime.success(writeFailure)).isFalse();
		assertThat(runtime.code(writeFailure)).isEqualTo("WRITE_FAILED");
		assertThat(runtime.store()).isEmpty();

		List<Object> tooManyInvalid = new ArrayList<>();
		for (int index = 0; index < 150; index++) {
			tooManyInvalid.add(runtime.row(" ", "user" + index + "@example.com", "13812345678",
				"110101199001" + String.format("%06d", index), index));
		}
		Object bounded = runtime.importRows(tooManyInvalid);
		assertThat(runtime.success(bounded)).isFalse();
		assertThat(runtime.errors(bounded)).hasSize(100);
		assertThat(runtime.store()).isEmpty();
	}

	@Test
	void generatedControllerExecutesIndependentPermissionsAndExistingAuditAspect() throws Exception {
		List<Map<String, String>> preview = generator().preview(1L);
		Class<?> controllerType = compileAndLoad(preview,
			"com.lotus.bixi.inventory.controller.InventoryItemController");
		try (GeneratedControllerRuntime runtime = new GeneratedControllerRuntime(controllerType)) {
			runtime.authenticate("inventory_inventory_item_view");
			assertThatThrownBy(runtime::importOneRow).isInstanceOf(AccessDeniedException.class);
			assertThatThrownBy(runtime::exportRows).isInstanceOf(AccessDeniedException.class);
			assertThat(runtime.importCalls()).isZero();
			assertThat(runtime.exportCalls()).isZero();

			runtime.authenticate("inventory_inventory_item_import");
			assertImportFailure(runtime, runtime.importNullFile(), "EMPTY_FILE", "导入文件不能为空");
			assertImportFailure(runtime, runtime.importEmptyFile(), "EMPTY_FILE", "导入文件不能为空");
			assertImportFailure(runtime, runtime.importEmptyWorkbook(), "EMPTY_FILE", "导入文件不包含数据行");
			assertImportFailure(runtime, runtime.importOversizedFile(), "FILE_TOO_LARGE", "导入文件不能超过5MB");
			assertImportFailure(runtime, runtime.importOversizedRequest(), "REQUEST_TOO_LARGE", "导入请求过大");
			assertImportFailure(runtime, runtime.importCorruptFile(), "INVALID_FILE", "导入文件无法解析");
			assertThat(runtime.importCalls()).isZero();

			runtime.clearAuditEvents();
			runtime.importOneRow();
			assertThat(runtime.importCalls()).isEqualTo(1);
			assertThat(runtime.auditEvents()).extracting(SysLogEventSource::getTitle)
				.containsExactly("导入库存物料");
			assertThat(runtime.auditEvents()).extracting(SysLogEventSource::getCreateBy)
				.containsExactly(41L);
			assertThat(runtime.persistedAuditLogs()).extracting(SysLog::getTitle)
				.containsExactly("导入库存物料");
			JsonNode importBody = new ObjectMapper().readTree(runtime.persistedAuditLogs().get(0).getParams());
			assertThat(importBody).hasSize(1);
			JsonNode fileMetadata = importBody.get(0);
			assertThat(fileMetadata.get("fieldName").asText()).isEqualTo("file");
			assertThat(fileMetadata.get("originalFilename").asText()).isEqualTo("inventory.xlsx");
			assertThat(fileMetadata.get("contentType").asText())
				.isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
			assertThat(fileMetadata.get("size").asLong()).isPositive();
			assertThat(fileMetadata.has("content")).isFalse();
			assertThat(fileMetadata.has("inputStream")).isFalse();
			assertThat(importBody.toString()).doesNotContain("Authorization", "credential-token",
				"MockHttpServletRequest");

			runtime.clearAuditEvents();
			runtime.authenticate("inventory_inventory_item_export");
			runtime.exportRows();
			assertThat(runtime.exportCalls()).isEqualTo(1);
			assertThat(runtime.auditEvents()).extracting(SysLogEventSource::getTitle)
				.containsExactly("导出库存物料");
		}
	}

	private static void assertImportFailure(GeneratedControllerRuntime runtime, Object response,
			String code, String message) throws Exception {
		assertThat(runtime.resultCode(response)).isEqualTo(code);
		assertThat(runtime.resultErrorMessages(response)).containsExactly(message);
		assertThat(runtime.resultErrorMessages(response))
			.noneMatch(error -> error.contains("com.alibaba") || error.contains("org.apache")
				|| error.toLowerCase(java.util.Locale.ROOT).contains("exception"));
	}

	@Test
	void writesJavaRenderedImportExportFrontendFixtureForNodeValidation() throws Exception {
		List<Map<String, String>> preview = generator().preview(1L);
		Path fixture = Files.createDirectories(Path.of(System.getProperty("basedir"), "target",
			"generated-import-export-frontend"));
		Files.writeString(fixture.resolve("inventoryItem.ts"), codeEndingWith(preview, "/inventoryItem.ts"));
		Files.writeString(fixture.resolve("index.vue"), codeEndingWith(preview, "/inventoryItem/index.vue"));
	}

	@SuppressWarnings("unchecked")
	private GeneratorServiceImpl generator() {
		BixiGeneratorDefaultProperties properties = new BixiGeneratorDefaultProperties();
		properties.setProjectRoot(projectRoot.toString());
		properties.setApiPath("api");
		properties.setBackendPath("biz");
		properties.setFrontendPath("ui");
		properties.setAllowedOutputRoots(List.of("api", "biz", "ui", "bixi-project-documents/sql"));

		GenTableColumnService columns = mock(GenTableColumnService.class);
		LambdaQueryChainWrapper<GenTableColumn> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
		when(columns.lambdaQuery()).thenReturn(query);
		when(query.eq(any(SFunction.class), any())).thenReturn(query);
		when(query.orderByAsc(any(SFunction.class))).thenReturn(query);
		when(query.list()).thenReturn(fields());

		GenFieldTypeService fieldTypes = mock(GenFieldTypeService.class);
		when(fieldTypes.getPackageByTableId("master", "biz_inventory_item")).thenReturn(Set.of());
		GenTableService tables = mock(GenTableService.class);
		when(tables.getById(1L)).thenReturn(table());
		return new GeneratorServiceImpl(properties, columns, fieldTypes, tables,
			mock(GenGroupService.class), new BuiltInTemplateCatalog());
	}

	private static String codeEndingWith(List<Map<String, String>> preview, String suffix) {
		return preview.stream().filter(entry -> entry.get("codePath").endsWith(suffix))
			.findFirst().orElseThrow().get("code");
	}

	private static GenTable table() {
		GenTable table = new GenTable();
		table.setId(1L);
		table.setDsName("master");
		table.setTableName("biz_inventory_item");
		table.setClassName("InventoryItem");
		table.setTableComment("库存物料");
		table.setPackageName("com.lotus.bixi");
		table.setModuleName("inventory");
		table.setFunctionName("inventoryItem");
		table.setStyle(BuiltInTemplateCatalog.DEFAULT_GROUP_ID);
		return table;
	}

	private static List<GenTableColumn> fields() {
		return List.of(
			field("id", "id", "Long", "主键", false, true),
			field("item_name", "itemName", "String", "物料名称", true, false),
			field("item_code", "itemCode", "String", "物料编码", false, false),
			field("description", "description", "String", "说明", false, false),
			field("monkey", "monkey", "String", "Monkey", false, false),
			field("donkey", "donkey", "String", "Donkey", false, false),
			field("email", "email", "String", "邮箱", false, false),
			field("phone", "phone", "String", "手机号", false, false),
			field("mobile", "mobile", "String", "移动电话", false, false),
			field("id_card", "idCard", "String", "证件号", false, false),
			field("identity", "identity", "String", "身份标识", false, false),
			field("api_token", "apiToken", "String", "访问令牌", false, false),
			field("access_key", "accessKey", "String", "访问密钥", false, false),
			field("encryption_key", "encryptionKey", "String", "加密密钥", false, false),
			field("signing_key", "signingKey", "String", "签名密钥", false, false),
			field("salt_value", "saltValue", "String", "盐值", false, false),
			field("pwd_hash", "pwdHash", "String", "密码摘要", false, false),
			field("private_key", "privateKey", "String", "私钥", false, false),
			field("api_key", "apiKey", "String", "API 密钥", false, false),
			field("user_password", "userPassword", "String", "密码", false, false),
			field("passwd_hash", "passwdHash", "String", "密码摘要", false, false),
			field("refresh_token", "refreshToken", "String", "刷新令牌", false, false),
			field("client_secret", "clientSecret", "String", "客户端密钥", false, false),
			field("login_credential", "loginCredential", "String", "登录凭证", false, false),
			field("credentials_blob", "credentialsBlob", "String", "凭证集合", false, false),
			field("quantity", "quantity", "Integer", "数量", true, false));
	}

	private static GenTableColumn field(String column, String property, String type, String comment,
			boolean required, boolean primary) {
		GenTableColumn field = new GenTableColumn();
		field.setFieldName(column);
		field.setAttrName(property);
		field.setAttrType(type);
		field.setFieldComment(comment);
		field.setFormRequired(required ? "1" : "0");
		field.setPrimaryPk(primary ? "1" : "0");
		field.setFormItem(primary ? "0" : "1");
		field.setGridItem(primary ? "0" : "1");
		field.setQueryItem("0");
		field.setQueryType("=");
		field.setFormType("text");
		return field;
	}

	private Class<?> compileAndLoad(List<Map<String, String>> preview, String className) throws Exception {
		List<Path> sources = new ArrayList<>();
		for (Map<String, String> artifact : preview) {
			if (!artifact.get("codePath").endsWith(".java")) continue;
			Path source = projectRoot.resolve(artifact.get("codePath"));
			Files.createDirectories(source.getParent());
			Files.writeString(source, artifact.get("code"));
			sources.add(source);
		}
		var compiler = ToolProvider.getSystemJavaCompiler();
		assertThat(compiler).as("tests must run on a JDK").isNotNull();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		Path classes = Files.createDirectories(projectRoot.resolve("compiled-import-export"));
		try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
			boolean compiled = compiler.getTask(null, files, diagnostics,
				List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()),
				null, files.getJavaFileObjectsFromPaths(sources)).call();
			assertThat(compiled).withFailMessage(() -> diagnostics.getDiagnostics().stream()
				.map(Object::toString).collect(Collectors.joining("\n"))).isTrue();
		}
		URLClassLoader loader = new URLClassLoader(new java.net.URL[] { classes.toUri().toURL() },
			getClass().getClassLoader());
		return Class.forName(className, true, loader);
	}

	private static final class GeneratedImportRuntime {

		private final Class<?> dtoType;
		private final Class<?> queryType;
		private final Class<?> rowErrorType;
		private final RuntimeStore store;
		private final Object service;
		private final Method importRows;
		private final Method exportRows;

		private GeneratedImportRuntime(Class<?> serviceType) throws Exception {
			ClassLoader loader = serviceType.getClassLoader();
			dtoType = Class.forName("com.lotus.bixi.inventory.api.dto.InventoryItemImportDTO", true, loader);
			queryType = Class.forName("com.lotus.bixi.inventory.api.dto.InventoryItemQueryDTO", true, loader);
			rowErrorType = Class.forName("com.lotus.bixi.inventory.api.dto.InventoryItemImportRowError", true, loader);
			Class<?> entityType = Class.forName("com.lotus.bixi.inventory.api.entity.InventoryItem", true, loader);
			Class<?> mapperType = Class.forName("com.lotus.bixi.inventory.mapper.InventoryItemMapper", true, loader);
			Class<?> serviceInterface = Class.forName("com.lotus.bixi.inventory.api.service.InventoryItemService", true, loader);
			store = new RuntimeStore(entityType);
			Object mapper = Proxy.newProxyInstance(loader, new Class<?>[] { mapperType }, store::invokeMapper);
			Object target = serviceType.getDeclaredConstructor().newInstance();
			Field baseMapper = serviceType.getSuperclass().getSuperclass().getDeclaredField("baseMapper");
			baseMapper.setAccessible(true);
			baseMapper.set(target, mapper);

			ProxyFactory proxyFactory = new ProxyFactory(target);
			proxyFactory.addAdvice(new TransactionInterceptor(new StoreTransactionManager(store),
				new AnnotationTransactionAttributeSource()));
			service = proxyFactory.getProxy(loader);
			importRows = serviceInterface.getMethod("importRows", List.class);
			exportRows = serviceInterface.getMethod("exportRows", queryType);
		}

		private Object row(String itemName, String email, String phone, String idCard,
				Integer quantity) throws Exception {
			Object row = dtoType.getDeclaredConstructor().newInstance();
			setProperty(row, "ItemName", itemName);
			setProperty(row, "Email", email);
			setProperty(row, "Phone", phone);
			setProperty(row, "IdCard", idCard);
			setProperty(row, "Quantity", quantity);
			return row;
		}

		private Object importRows(List<?> rows) {
			return invoke(importRows, rows);
		}

		@SuppressWarnings("unchecked")
		private List<?> exportRows() throws Exception {
			return (List<?>) invoke(exportRows, queryType.getDeclaredConstructor().newInstance());
		}

		private boolean success(Object result) throws Exception {
			return (Boolean) result.getClass().getMethod("isSuccess").invoke(result);
		}

		private int importedRows(Object result) throws Exception {
			return (Integer) result.getClass().getMethod("getImportedRows").invoke(result);
		}

		private String code(Object result) throws Exception {
			return (String) result.getClass().getMethod("getCode").invoke(result);
		}

		@SuppressWarnings("unchecked")
		private List<?> errors(Object result) throws Exception {
			return (List<?>) result.getClass().getMethod("getErrors").invoke(result);
		}

		private List<Integer> errorRows(Object result) throws Exception {
			List<Integer> rows = new ArrayList<>();
			for (Object error : errors(result)) {
				rows.add((Integer) error.getClass().getMethod("getRowNumber").invoke(error));
			}
			return rows;
		}

		@SuppressWarnings("unchecked")
		private List<String> errorMessages(Object result) throws Exception {
			List<String> messages = new ArrayList<>();
			for (Object error : errors(result)) {
				messages.addAll((List<String>) error.getClass().getMethod("getErrors").invoke(error));
			}
			return messages;
		}

		private Object rowError(int rowNumber, List<String> messages) {
			try {
				return rowErrorType.getConstructor(int.class, List.class).newInstance(rowNumber, messages);
			}
			catch (InvocationTargetException failure) {
				Throwable cause = failure.getCause();
				if (cause instanceof RuntimeException runtime) throw runtime;
				throw new IllegalStateException(cause);
			}
			catch (ReflectiveOperationException failure) {
				throw new IllegalStateException(failure);
			}
		}

		@SuppressWarnings("unchecked")
		private List<String> rowErrorMessages(Object rowError) throws Exception {
			return (List<String>) rowError.getClass().getMethod("getErrors").invoke(rowError);
		}

		private Object property(Object target, String property) throws Exception {
			return target.getClass().getMethod("get" + property).invoke(target);
		}

		private List<StoredRow> store() {
			return store.snapshot();
		}

		private void clear() {
			store.clear();
		}

		private void failDuplicate(String itemName) {
			store.failDuplicate(itemName);
		}

		private void failNthInsert(int nth) {
			store.failNthInsert(nth);
		}

		private Object invoke(Method method, Object... args) {
			try {
				return method.invoke(service, args);
			}
			catch (InvocationTargetException failure) {
				Throwable cause = failure.getCause();
				if (cause instanceof RuntimeException runtime) throw runtime;
				throw new IllegalStateException(cause);
			}
			catch (ReflectiveOperationException failure) {
				throw new IllegalStateException(failure);
			}
		}
	}

	private static final class GeneratedControllerRuntime implements AutoCloseable {
		private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		private final List<SysLogEventSource> auditEvents = new ArrayList<>();
		private final List<SysLog> persistedAuditLogs = new ArrayList<>();
		private final AtomicInteger importCalls = new AtomicInteger();
		private final AtomicInteger exportCalls = new AtomicInteger();
		private final Object controller;
		private final Class<?> dtoType;
		private final Class<?> queryType;
		private final Method importData;
		private final Method export;

		@SuppressWarnings({"rawtypes", "unchecked"})
		private GeneratedControllerRuntime(Class<?> controllerType) throws Exception {
			ClassLoader loader = controllerType.getClassLoader();
			OperationLogService persistence = mock(OperationLogService.class);
			when(persistence.saveLog(any(SysLog.class))).thenAnswer(invocation -> {
				persistedAuditLogs.add(invocation.getArgument(0));
				return null;
			});
			BixiLogProperties logProperties = new BixiLogProperties();
			SysLogListener listener = new SysLogListener(persistence, logProperties);
			listener.afterPropertiesSet();
			context.setClassLoader(loader);
			dtoType = Class.forName("com.lotus.bixi.inventory.api.dto.InventoryItemImportDTO", true, loader);
			queryType = Class.forName("com.lotus.bixi.inventory.api.dto.InventoryItemQueryDTO", true, loader);
			Class<?> resultType = Class.forName("com.lotus.bixi.inventory.api.dto.InventoryItemImportResult", true, loader);
		Class<?> serviceType = Class.forName("com.lotus.bixi.inventory.api.service.InventoryItemService", true, loader);
			Method success = resultType.getMethod("success", int.class);
			Object service = Proxy.newProxyInstance(loader, new Class<?>[] { serviceType }, (proxy, method, args) -> {
				if (method.getDeclaringClass() == Object.class) {
					return switch (method.getName()) {
						case "toString" -> "GeneratedInventoryItemService";
						case "hashCode" -> System.identityHashCode(proxy);
						case "equals" -> proxy == args[0];
						default -> throw new UnsupportedOperationException(method.toString());
					};
				}
				return switch (method.getName()) {
					case "importRows" -> {
						importCalls.incrementAndGet();
						yield success.invoke(null, ((List<?>) args[0]).size());
					}
					case "exportRows" -> {
						exportCalls.incrementAndGet();
						yield List.of();
					}
					default -> throw new UnsupportedOperationException("Unexpected service call: " + method);
				};
			});

			context.register(DynamicControllerSecurityConfig.class);
			context.addApplicationListener(event -> {
				if (event instanceof SysLogEvent logEvent && logEvent.getSource() instanceof SysLogEventSource source) {
					auditEvents.add(source);
					listener.saveSysLog(logEvent);
				}
			});
			context.registerBean("generatedInventoryItemController", (Class) controllerType, () -> {
				try {
					return controllerType.getConstructor(serviceType).newInstance(service);
				}
				catch (ReflectiveOperationException failure) {
					throw new IllegalStateException(failure);
				}
			});
			context.refresh();
			controller = context.getBean(controllerType);
			importData = controllerType.getMethod("importData",
				org.springframework.web.multipart.MultipartFile.class, jakarta.servlet.http.HttpServletRequest.class);
			export = controllerType.getMethod("export", queryType);
		}

		private void authenticate(String permission) {
			BixiUser actor = new BixiUser(41L, 1L, 7L, "generator-tester", "unused", null,
				true, true, true, true, AuthorityUtils.createAuthorityList(permission));
			SecurityContextHolder.getContext().setAuthentication(
				UsernamePasswordAuthenticationToken.authenticated(actor, "", actor.getAuthorities()));
		}

		private void importOneRow() throws Exception {
			Object row = dtoType.getDeclaredConstructor().newInstance();
			setProperty(row, "ItemName", "Imported");
			setProperty(row, "Email", "import@example.com");
			setProperty(row, "Phone", "13812345678");
			setProperty(row, "IdCard", "110101199001013456");
			setProperty(row, "Quantity", 1);
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			EasyExcel.write(output, dtoType).autoCloseStream(false).sheet().doWrite(List.of(row));
			byte[] workbook = output.toByteArray();
			MockMultipartFile file = new MockMultipartFile("file", "inventory.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook);
			MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inventoryItem/import");
			request.setContent(workbook);
			request.addHeader("Authorization", "Bearer credential-token");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
			importFile(file, request);
		}

		private Object importNullFile() {
			return importFile(null, new MockHttpServletRequest("POST", "/inventoryItem/import"));
		}

		private Object importEmptyFile() {
			return importFile(new MockMultipartFile("file", "empty.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]),
				new MockHttpServletRequest("POST", "/inventoryItem/import"));
		}

		private Object importEmptyWorkbook() throws Exception {
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			EasyExcel.write(output, dtoType).autoCloseStream(false).sheet().doWrite(List.of());
			byte[] workbook = output.toByteArray();
			MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inventoryItem/import");
			request.setContent(workbook);
			return importFile(new MockMultipartFile("file", "empty-workbook.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook), request);
		}

		private Object importOversizedFile() {
			MultipartFile file = mock(MultipartFile.class);
			when(file.isEmpty()).thenReturn(false);
			when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);
			return importFile(file, new MockHttpServletRequest("POST", "/inventoryItem/import"));
		}

		private Object importOversizedRequest() {
			MockHttpServletRequest request = new MockHttpServletRequest("POST", "/inventoryItem/import");
			request.setContent(new byte[5 * 1024 * 1024 + 64 * 1024 + 1]);
			return importFile(new MockMultipartFile("file", "small.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1 }), request);
		}

		private Object importCorruptFile() {
			return importFile(new MockMultipartFile("file", "corrupt.xlsx",
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
				"not an Excel workbook".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				new MockHttpServletRequest("POST", "/inventoryItem/import"));
		}

		private Object importFile(MultipartFile file, MockHttpServletRequest request) {
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
			try {
				return invoke(importData, file, request);
			}
			finally {
				RequestContextHolder.resetRequestAttributes();
			}
		}

		private String resultCode(Object response) throws Exception {
			Object result = response.getClass().getMethod("getData").invoke(response);
			return (String) result.getClass().getMethod("getCode").invoke(result);
		}

		@SuppressWarnings("unchecked")
		private List<String> resultErrorMessages(Object response) throws Exception {
			Object result = response.getClass().getMethod("getData").invoke(response);
			List<Object> errors = (List<Object>) result.getClass().getMethod("getErrors").invoke(result);
			List<String> messages = new ArrayList<>();
			for (Object error : errors) {
				messages.addAll((List<String>) error.getClass().getMethod("getErrors").invoke(error));
			}
			return messages;
		}

		private void exportRows() throws Exception {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", "/inventoryItem/export");
			RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
			try {
				invoke(export, queryType.getDeclaredConstructor().newInstance());
			}
			finally {
				RequestContextHolder.resetRequestAttributes();
			}
		}

		private Object invoke(Method method, Object... args) {
			try {
				return method.invoke(controller, args);
			}
			catch (InvocationTargetException failure) {
				Throwable cause = failure.getCause();
				if (cause instanceof RuntimeException runtime) throw runtime;
				throw new IllegalStateException(cause);
			}
			catch (ReflectiveOperationException failure) {
				throw new IllegalStateException(failure);
			}
		}

		private int importCalls() {
			return importCalls.get();
		}

		private int exportCalls() {
			return exportCalls.get();
		}

		private List<SysLogEventSource> auditEvents() {
			return List.copyOf(auditEvents);
		}

		private List<SysLog> persistedAuditLogs() {
			return List.copyOf(persistedAuditLogs);
		}

		private void clearAuditEvents() {
			auditEvents.clear();
			persistedAuditLogs.clear();
		}

		@Override
		public void close() {
			SecurityContextHolder.clearContext();
			RequestContextHolder.resetRequestAttributes();
			context.close();
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableAspectJAutoProxy(proxyTargetClass = true)
	@EnableMethodSecurity
	static class DynamicControllerSecurityConfig {
		@Bean
		static PrePostTemplateDefaults prePostTemplateDefaults() {
			return new PrePostTemplateDefaults();
		}

		@Bean("pms")
		PermissionService permissionService() {
			return new PermissionService();
		}

		@Bean
		SysLogAspect sysLogAspect() {
			return new SysLogAspect();
		}

		@Bean
		BixiLogProperties logProperties() {
			return new BixiLogProperties();
		}

		@Bean
		SpringContextHolder springContextHolder() {
			return new SpringContextHolder();
		}

		@Bean
		static SpringUtil springUtil() {
			return new SpringUtil();
		}
	}

	private static final class RuntimeStore {
		private final Class<?> entityType;
		private final List<StoredRow> rows = new ArrayList<>();
		private int insertAttempts;
		private int failAttempt = -1;
		private String duplicateItemName;

		private RuntimeStore(Class<?> entityType) {
			this.entityType = entityType;
		}

		private Object invokeMapper(Object proxy, Method method, Object[] args) throws Exception {
			if (method.getDeclaringClass() == Object.class) {
				return switch (method.getName()) {
					case "toString" -> "ImportMapperProxy";
					case "hashCode" -> System.identityHashCode(proxy);
					case "equals" -> proxy == args[0];
					default -> throw new UnsupportedOperationException(method.toString());
				};
			}
			return switch (method.getName()) {
				case "insert" -> insert(args[0]);
				case "selectList" -> entities();
				default -> throw new UnsupportedOperationException("Unexpected mapper call: " + method);
			};
		}

		private int insert(Object entity) throws Exception {
			int attempt = ++insertAttempts;
			String itemName = (String) getProperty(entity, "ItemName");
			if (Objects.equals(itemName, duplicateItemName)) {
				duplicateItemName = null;
				throw new DuplicateKeyException("constraint uk_inventory_item raw=" + itemName);
			}
			if (attempt == failAttempt) {
				failAttempt = -1;
				return 0;
			}
			rows.add(new StoredRow(itemName, (String) getProperty(entity, "Email"),
				(String) getProperty(entity, "Phone"), (String) getProperty(entity, "IdCard"),
				(Integer) getProperty(entity, "Quantity")));
			return 1;
		}

		private List<Object> entities() throws Exception {
			List<Object> entities = new ArrayList<>();
			for (StoredRow row : rows) {
				Object entity = entityType.getDeclaredConstructor().newInstance();
				setProperty(entity, "ItemName", row.itemName());
				setProperty(entity, "Email", row.email());
				setProperty(entity, "Phone", row.phone());
				setProperty(entity, "IdCard", row.idCard());
				setProperty(entity, "Quantity", row.quantity());
				entities.add(entity);
			}
			return entities;
		}

		private List<StoredRow> snapshot() {
			return List.copyOf(rows);
		}

		private void restore(List<StoredRow> snapshot) {
			rows.clear();
			rows.addAll(snapshot);
		}

		private void clear() {
			rows.clear();
			insertAttempts = 0;
			failAttempt = -1;
			duplicateItemName = null;
		}

		private void failDuplicate(String itemName) {
			duplicateItemName = itemName;
		}

		private void failNthInsert(int nth) {
			failAttempt = insertAttempts + nth;
		}
	}

	private static final class StoreTransactionManager extends AbstractPlatformTransactionManager {
		private final RuntimeStore store;

		private StoreTransactionManager(RuntimeStore store) {
			this.store = store;
		}

		@Override
		protected Object doGetTransaction() {
			return new StoreTransaction();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			((StoreTransaction) transaction).before = store.snapshot();
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			store.restore(((StoreTransaction) status.getTransaction()).before);
		}
	}

	private static final class StoreTransaction {
		private List<StoredRow> before;
	}

	private record StoredRow(String itemName, String email, String phone, String idCard,
		Integer quantity) {
	}

	private static Object getProperty(Object target, String property) throws Exception {
		return target.getClass().getMethod("get" + property).invoke(target);
	}

	private static void setProperty(Object target, String property, Object value) throws Exception {
		Method setter = java.util.Arrays.stream(target.getClass().getMethods())
			.filter(method -> method.getName().equals("set" + property) && method.getParameterCount() == 1)
			.findFirst().orElseThrow();
		setter.invoke(target, value);
	}

}
