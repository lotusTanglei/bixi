package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.service.GenFieldTypeService;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.service.GenTableService;
import com.lotus.bixi.generator.template.BuiltInTemplateCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeneratorServiceImplTest {

	@TempDir
	Path projectRoot;

	private GenTableService tableService;
	private GenTableColumnService columns;
	private GenFieldTypeService fieldTypes;
	private GenGroupService groupService;
	private BixiGeneratorDefaultProperties properties;
	private GeneratorServiceImpl generator;
	private GenTable firstTable;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		properties = new BixiGeneratorDefaultProperties();
		properties.setProjectRoot(projectRoot.toString());
		properties.setApiPath("api");
		properties.setBackendPath("biz");
		properties.setFrontendPath("ui");
		properties.setAllowedOutputRoots(List.of("api", "biz", "ui", "bixi-project-documents/sql"));

		columns = mock(GenTableColumnService.class);
		LambdaQueryChainWrapper<GenTableColumn> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
		when(columns.lambdaQuery()).thenReturn(query);
		when(query.eq(any(SFunction.class), any())).thenReturn(query);
		when(query.orderByAsc(any(SFunction.class))).thenReturn(query);
		when(query.list()).thenReturn(fields());

		fieldTypes = mock(GenFieldTypeService.class);
		when(fieldTypes.getPackageByTableId("master", "biz_inventory_item")).thenReturn(Set.of());

		tableService = mock(GenTableService.class);
		firstTable = table(1L);
		when(tableService.getById(1L)).thenReturn(firstTable);
		when(tableService.getById(2L)).thenReturn(table(2L));
		groupService = mock(GenGroupService.class);
		generator = new GeneratorServiceImpl(properties, columns, fieldTypes, tableService, groupService,
			new BuiltInTemplateCatalog());
	}

	@Test
	void cleanDatabaseUsesTheFixedCatalogForTheDefaultGroup() {
		List<Map<String, String>> preview = generator.preview(1L);

		assertThat(preview).hasSize(17);
		String templateVersion = preview.get(0).get("templateVersion");
		assertThat(templateVersion).isNotBlank();
		assertThat(preview).extracting(entry -> entry.get("templateVersion")).containsOnly(templateVersion);
		assertThat(preview).extracting(entry -> entry.get("codePath"))
			.anyMatch(path -> path.startsWith("api/"))
			.anyMatch(path -> path.startsWith("biz/"))
			.anyMatch(path -> path.startsWith("ui/"));
		assertThat(preview).extracting(entry -> entry.get("code"))
			.allSatisfy(code -> assertThat(code).doesNotContain("${", "$ts", "$!"));
	}

	@Test
	void legacyTableWithoutStyleUsesTheFixedCatalog() {
		GenTable legacy = table(4L);
		legacy.setStyle(null);
		when(tableService.getById(4L)).thenReturn(legacy);

		List<Map<String, String>> preview = generator.preview(4L);

		assertThat(preview).hasSize(17);
		verifyNoInteractions(groupService);
	}

	@Test
	void previewVersionPublishesTheExactPreviewedCatalogSnapshot() throws IOException {
		List<Map<String, String>> preview = generator.preview(1L);
		String version = preview.get(0).get("templateVersion");

		List<Path> written = generator.generatorCode(List.of(1L), version, false);

		assertThat(written).hasSize(17).allSatisfy(path -> assertThat(path).isRegularFile());
		for (Map<String, String> expected : preview) {
			Path path = projectRoot.resolve(expected.get("codePath"));
			assertThat(Files.readString(path)).isEqualTo(expected.get("code"));
		}
	}

	@Test
	void rejectsAStalePreviewVersionWithoutWritingFiles() throws IOException {
		assertThatThrownBy(() -> generator.generatorCode(List.of(1L), "stale-version", false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("模板已变化");

		assertThat(regularFiles()).isEmpty();
	}

	@Test
	void metadataChangeInvalidatesPreviewVersionWithoutWritingFiles() throws IOException {
		String version = generator.preview(1L).get(0).get("templateVersion");
		firstTable.setFunctionName("renamedInventoryItem");

		assertThatThrownBy(() -> generator.generatorCode(List.of(1L), version, false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("重新预览");

		assertThat(regularFiles()).isEmpty();
	}

	@Test
	void previewVersionCannotBeReusedForAnotherTable() throws IOException {
		String version = generator.preview(1L).get(0).get("templateVersion");

		assertThatThrownBy(() -> generator.generatorCode(List.of(2L), version, false))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("重新预览");

		assertThat(regularFiles()).isEmpty();
	}

	@Test
	void zipDownloadRejectsMissingOrStalePreviewVersion() throws IOException {
		try (ZipOutputStream missingZip = new ZipOutputStream(new ByteArrayOutputStream())) {
			assertThatThrownBy(() -> generator.downloadCode(1L, null, missingZip))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("预览");
		}
		try (ZipOutputStream staleZip = new ZipOutputStream(new ByteArrayOutputStream())) {
			assertThatThrownBy(() -> generator.downloadCode(1L, "stale-version", staleZip))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("重新预览");
		}
	}

	@Test
	void zipDownloadPublishesTheExactPreviewedSnapshot() throws IOException {
		List<Map<String, String>> preview = generator.preview(1L);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			generator.downloadCode(1L, preview.get(0).get("templateVersion"), zip);
		}

		assertThat(bytes.toByteArray()).startsWith((byte) 'P', (byte) 'K');
	}

	@Test
	void zipDownloadUsesProjectRelativeEntriesWhenProjectRootIsASymbolicLink() throws IOException {
		Path linkedRoot = Files.createTempFile(projectRoot.getParent(), "generator-root-link-", "");
		Files.delete(linkedRoot);
		Files.createSymbolicLink(linkedRoot, projectRoot);
		properties.setProjectRoot(linkedRoot.toString());
		GeneratorServiceImpl linkedGenerator = new GeneratorServiceImpl(properties, columns, fieldTypes, tableService,
			groupService, new BuiltInTemplateCatalog());

		List<Map<String, String>> preview = linkedGenerator.preview(1L);
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			linkedGenerator.downloadCode(1L, preview.get(0).get("templateVersion"), zip);
		}

		try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray()))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				assertThat(entry.getName()).doesNotContain("..");
			}
		}
	}

	@Test
	void rejectsBatchGenerationWithoutAPreviewVersionForEachTable() throws IOException {
		String version = generator.preview(1L).get(0).get("templateVersion");

		assertThatThrownBy(() -> generator.generatorCode(List.of(1L, 2L), version, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("逐表预览");

		assertThat(regularFiles()).isEmpty();
	}

	@Test
	void missingCustomGroupDoesNotFallBackToTheDefaultCatalog() {
		GenTable custom = table(3L);
		custom.setStyle(99L);
		when(tableService.getById(3L)).thenReturn(custom);

		assertThatThrownBy(() -> generator.preview(3L))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("没有可用模板");
	}

	@Test
	void generatedJavaCompilesAgainstTheProjectClasspath() throws IOException {
		List<Path> sources = new ArrayList<>();
		for (Map<String, String> artifact : generator.preview(1L)) {
			if (artifact.get("codePath").endsWith(".java")) {
				Path source = projectRoot.resolve(artifact.get("codePath"));
				Files.createDirectories(source.getParent());
				Files.writeString(source, artifact.get("code"));
				sources.add(source);
			}
		}

		var compiler = ToolProvider.getSystemJavaCompiler();
		assertThat(compiler).as("tests must run on a JDK").isNotNull();
		var diagnostics = new DiagnosticCollector<JavaFileObject>();
		Path classes = Files.createDirectories(projectRoot.resolve("compiled"));
		try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null)) {
			var units = files.getJavaFileObjectsFromPaths(sources);
			boolean compiled = compiler.getTask(null, files, diagnostics,
				List.of("-classpath", System.getProperty("java.class.path"), "-d", classes.toString()),
				null, units).call();
			assertThat(compiled)
				.withFailMessage(() -> diagnostics.getDiagnostics().stream()
					.map(Object::toString).collect(Collectors.joining(System.lineSeparator())))
				.isTrue();
		}
	}

	private List<Path> regularFiles() throws IOException {
		try (var paths = Files.walk(projectRoot)) {
			return paths.filter(Files::isRegularFile).toList();
		}
	}

	private static GenTable table(Long id) {
		GenTable table = new GenTable();
		table.setId(id);
		table.setDsName("master");
		table.setDbType("MySQL");
		table.setTableName("biz_inventory_item");
		table.setClassName("InventoryItem");
		table.setTableComment("库存物料");
		table.setPackageName("com.lotus.bixi");
		table.setVersion("1.0.0");
		table.setModuleName("inventory");
		table.setFunctionName("inventoryItem");
		table.setFormLayout(2);
		table.setStyle(BuiltInTemplateCatalog.DEFAULT_GROUP_ID);
		table.setAuthor("bixi");
		return table;
	}

	private static List<GenTableColumn> fields() {
		return List.of(
			field("id", "id", "Long", true, false, false, false),
			field("item_name", "itemName", "String", false, true, true, true),
			field("quantity", "quantity", "Integer", false, true, true, false),
			field("create_time", "createTime", "LocalDateTime", false, false, true, false)
		);
	}

	private static GenTableColumn field(String name, String attrName, String attrType, boolean primary,
			boolean form, boolean grid, boolean query) {
		GenTableColumn field = new GenTableColumn();
		field.setFieldName(name);
		field.setAttrName(attrName);
		field.setAttrType(attrType);
		field.setFieldComment(name);
		field.setPrimaryPk(primary ? "1" : "0");
		field.setFormItem(form ? "1" : "0");
		field.setFormRequired("item_name".equals(name) ? "1" : "0");
		field.setGridItem(grid ? "1" : "0");
		field.setQueryItem(query ? "1" : "0");
		field.setQueryType("String".equals(attrType) ? "like" : "=");
		field.setFormType("text");
		return field;
	}
}
