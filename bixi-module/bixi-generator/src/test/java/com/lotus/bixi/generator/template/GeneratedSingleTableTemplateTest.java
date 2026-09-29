package com.lotus.bixi.generator.template;

import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.util.VelocityKit;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedSingleTableTemplateTest {

	@Test
	void rendersACompleteApiBizSqlAndVueBundleWithoutUnresolvedReferences() throws IOException {
		List<GenTableColumn> fields = List.of(
			field("id", "id", "Long", "主键", false, true, false, false),
			field("item_name", "itemName", "String", "物料名称", true, false, true, true),
			field("quantity", "quantity", "Integer", "数量", true, false, true, false),
			field("due_date", "dueDate", "LocalDate", "到期日期", false, false, true, false),
			field("remark", "remark", "String", "备注", false, false, true, false),
			field("create_time", "createTime", "LocalDateTime", "创建时间", false, false, true, false)
		);
		List<GenTableColumn> formFields = fields.stream()
			.filter(field -> "1".equals(field.getFormItem())).toList();
		List<GenTableColumn> gridFields = fields.stream()
			.filter(field -> "1".equals(field.getGridItem())).toList();
		List<GenTableColumn> queryFields = fields.stream()
			.filter(field -> "1".equals(field.getQueryItem())).toList();
		List<GenTableColumn> deliveryFields = fields.stream()
			.filter(field -> "1".equals(field.getFormItem()))
			.filter(field -> !Set.of("id", "remark", "create_time").contains(field.getFieldName()))
			.toList();

		Map<String, Object> model = new HashMap<>();
		model.put("package", "com.lotus.bixi");
		model.put("packagePath", "com/lotus/bixi");
		model.put("moduleName", "inventory");
		model.put("functionName", "inventoryItem");
		model.put("ClassName", "InventoryItem");
		model.put("className", "inventoryItem");
		model.put("tableName", "biz_inventory_item");
		model.put("tableComment", "库存物料");
		model.put("permissionPrefix", "inventory_inventory_item");
		model.put("apiPath", "bixi-module/bixi-upms-api");
		model.put("backendPath", "bixi-module/bixi-upms-biz");
		model.put("frontendPath", "bixi-ui");
		model.put("importList", Set.of("java.time.LocalDate", "java.time.LocalDateTime"));
		model.put("fieldList", fields);
		model.put("formList", formFields);
		model.put("gridList", gridFields);
		model.put("queryList", queryFields);
		model.put("importFieldList", deliveryFields);
		model.put("exportFieldList", deliveryFields);
		model.put("emailExportFields", Set.of());
		model.put("phoneExportFields", Set.of());
		model.put("identityExportFields", Set.of());
		model.put("hasRequiredImportFields", true);
		model.put("hasRequiredFields", true);
		model.put("hasRequiredStringFields", true);

		Map<String, Rendered> rendered = new LinkedHashMap<>();
		for (var template : new BuiltInTemplateCatalog().snapshot().templates()) {
			String kind = template.getTemplateName().substring(0, template.getTemplateName().indexOf('@'));
			String path = VelocityKit.renderStr(template.getGeneratorPath(), model);
			String code = VelocityKit.renderStr(template.getTemplateCode(), model);
			rendered.put(kind, new Rendered(path, code));
			assertThat(path).doesNotContain("$").doesNotContain("..").doesNotStartWith("/");
			assertThat(code).as(kind).doesNotContain("$ts").doesNotContain("$!").doesNotContain("${");
		}

		assertThat(rendered).hasSize(17);
		assertThat(rendered.get("entity").path()).startsWith("bixi-module/bixi-upms-api/");
		assertThat(rendered.get("service").path()).startsWith("bixi-module/bixi-upms-api/");
		assertThat(rendered.get("service").code())
				.contains("package com.lotus.bixi.inventory.api.service;")
				.doesNotContain("extends IService<InventoryItem>");
		assertThat(rendered.get("entity").code())
				.contains("extends BaseEntity<InventoryItem>")
				.doesNotContain("tenantId;");
		assertThat(rendered.get("controller").path()).startsWith("bixi-module/bixi-upms-biz/");
		assertThat(rendered.get("controller").code())
				.contains("@RestController(\"inventoryInventoryItemController\")")
			.contains("R<IPage<InventoryItemVO>> page")
			.contains("R<InventoryItemVO> details")
			.contains("@HasPermission(\"inventory_inventory_item_view\")")
			.contains("@HasPermission(\"inventory_inventory_item_add\")")
			.contains("@HasPermission(\"inventory_inventory_item_edit\")")
			.contains("@HasPermission(\"inventory_inventory_item_del\")")
			.contains("@HasPermission(\"inventory_inventory_item_import\")")
			.contains("@HasPermission(\"inventory_inventory_item_export\")")
			.contains("@Valid @RequestBody InventoryItemCreateDTO")
			.contains("@Valid @RequestBody InventoryItemUpdateDTO")
			.contains("@SysLog(\"新增库存物料\")")
			.contains("@SysLog(\"修改库存物料\")")
			.contains("@SysLog(\"删除库存物料\")")
			.contains("@SysLog(\"导入库存物料\")")
			.contains("@SysLog(\"导出库存物料\")");
		assertThat(rendered.get("create-dto").code()).contains("@NotBlank").contains("itemName");
		assertThat(rendered.get("update-dto").code()).contains("@NotNull").contains("Long id");
		assertThat(rendered.get("query-dto").code()).contains("itemName");
		assertThat(rendered.get("vo").code()).contains("Long id", "itemName", "createTime")
				.doesNotContain("tenantId");
		assertThat(rendered.get("import-dto").code())
			.contains("itemName", "quantity", "dueDate")
			.doesNotContain("remark", "createTime");
		assertThat(rendered.get("export-vo").code())
			.contains("itemName", "quantity", "dueDate")
			.doesNotContain("remark", "createTime");
		assertThat(rendered.get("service-impl").code())
				.contains("@Service(\"inventoryInventoryItemService\")")
				.contains("@Transactional", "pageInventoryItem", "InventoryItemVO details");
		assertThat(rendered.get("mapper").code())
				.contains("@Mapper", "@Repository(\"inventoryInventoryItemMapper\")")
				.contains("import com.lotus.bixi.common.mybatis.annotation.DataScope;")
				.contains("@DataScope(userAlias = \"\", userColumn = \"create_by\", creatorScope = true)");
		assertThat(rendered.get("menu-sql").code())
			.contains("inventory_inventory_item_view", "inventory_inventory_item_add",
					"inventory_inventory_item_edit", "inventory_inventory_item_del",
					"inventory_inventory_item_import", "inventory_inventory_item_export");
		assertThat(rendered.get("frontend-list").code())
			.contains("v-loading=\"state.loading\"")
			.contains("loadError")
			.contains("empty-text=\"暂无数据\"")
			.contains("v-auth=\"'inventory_inventory_item_add'\"")
			.contains("v-auth=\"'inventory_inventory_item_edit'\"")
			.contains("v-auth=\"'inventory_inventory_item_del'\"");
		assertThat(rendered.get("frontend-form").code())
				.contains("width=\"min(600px, 92vw)\"", "v-loading=\"loading\"", ":rules=\"rules\"")
				.contains("itemName", "required: true", "加载失败", "保存失败");

		Path fixture = Files.createDirectories(Path.of(System.getProperty("basedir"), "target",
				"generated-single-table-frontend"));
		Files.writeString(fixture.resolve("inventoryItem.ts"), rendered.get("frontend-api").code());
		Files.writeString(fixture.resolve("index.vue"), rendered.get("frontend-list").code());
		Files.writeString(fixture.resolve("form.vue"), rendered.get("frontend-form").code());
	}

	private static GenTableColumn field(String column, String property, String type, String comment,
			boolean required, boolean primary, boolean visible, boolean query) {
		GenTableColumn field = new GenTableColumn();
		field.setFieldName(column);
		field.setAttrName(property);
		field.setAttrType(type);
		field.setFieldComment(comment);
		field.setFormRequired(required ? "1" : "0");
		field.setPrimaryPk(primary ? "1" : "0");
		field.setFormItem(visible ? "1" : "0");
		field.setGridItem(visible ? "1" : "0");
		field.setQueryItem(query ? "1" : "0");
		field.setQueryType("String".equals(type) ? "like" : "=");
		field.setFormType(type.startsWith("LocalDate") ? "date" : "text");
		return field;
	}

	private record Rendered(String path, String code) {
	}
}
