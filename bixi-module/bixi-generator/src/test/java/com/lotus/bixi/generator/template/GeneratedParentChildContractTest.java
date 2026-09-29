package com.lotus.bixi.generator.template;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.service.GenFieldTypeService;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.service.GenTableService;
import com.lotus.bixi.generator.service.impl.GeneratorServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

class GeneratedParentChildContractTest {

	@TempDir
	Path projectRoot;

	@Test
	void rejectsIncompleteRelationshipMetadataBeforeRendering() {
		GenTable onlyChildTable = parentTable();
		onlyChildTable.setChildTableName("purchase_order_item");
		assertInvalidRelationship(onlyChildTable, "父子表关系元数据必须同时配置");

		GenTable onlyMainField = parentTable();
		onlyMainField.setMainField("id");
		assertInvalidRelationship(onlyMainField, "父子表关系元数据必须同时配置");

		GenTable onlyChildField = parentTable();
		onlyChildField.setChildField("order_id");
		assertInvalidRelationship(onlyChildField, "父子表关系元数据必须同时配置");
	}

	@Test
	void rejectsSelfReferencingRelationshipBeforeRendering() {
		GenTable table = parentTable();
		table.setChildTableName(table.getTableName());
		table.setMainField("id");
		table.setChildField("id");

		assertInvalidRelationship(table, "子表不能与主表相同");
	}

	@Test
	void rejectsANonPrimaryParentRelationshipKeyBeforeRendering() {
		GenTable table = parentChildTable();
		table.setMainField("order_no");
		table.setChildField("sku");

		assertInvalidRelationship(table, "主表关联键必须是主键");
	}

	@Test
	void rejectsAPrimaryChildRelationshipKeyBeforeRendering() {
		GenTable table = parentChildTable();
		table.setChildField("id");

		assertInvalidRelationship(table, "子表关联键不能是主键");
	}

	@Test
	void rejectsParentRelationshipKeysOutsideTheLongIdContract() {
		GenTable stringIdTable = parentChildTable();
		assertInvalidRelationship(stringIdTable,
			List.of(field("id", "id", "String", true, false),
				field("order_no", "orderNo", "String", false, true)),
			childFields(), "主表关联键必须是 Long 类型的 id 主键");

		GenTable integerIdTable = parentChildTable();
		assertInvalidRelationship(integerIdTable,
			List.of(field("id", "id", "Integer", true, false),
				field("order_no", "orderNo", "String", false, true)),
			childFields(), "主表关联键必须是 Long 类型的 id 主键");

		GenTable nonIdTable = parentChildTable();
		nonIdTable.setMainField("external_id");
		assertInvalidRelationship(nonIdTable,
			List.of(field("id", "id", "Long", true, false),
				field("external_id", "externalId", "Long", true, false),
				field("order_no", "orderNo", "String", false, true)),
			childFields(), "主表关联键必须是 Long 类型的 id 主键");
	}

	@Test
	void rejectsManagedChildRelationshipKeysBeforeRendering() {
		GenTable tenantRelation = parentChildTable();
		tenantRelation.setChildField("tenant_id");
		assertInvalidRelationship(tenantRelation, parentFields(), childFields(),
			"子表关联键不能使用基础字段");

		GenTable creatorRelation = parentChildTable();
		creatorRelation.setChildField("create_by");
		List<GenTableColumn> fields = new ArrayList<>(childFields());
		fields.add(field("create_by", "createBy", "Long", false, false));
		assertInvalidRelationship(creatorRelation, parentFields(), fields,
			"子表关联键不能使用基础字段");
	}

	@Test
	void rejectsCaseInsensitiveDuplicateRelationshipMetadata() {
		List<GenTableColumn> duplicateParentFields = new ArrayList<>(parentFields());
		duplicateParentFields.add(field("ID", "duplicateId", "Long", true, false));
		assertInvalidRelationship(parentChildTable(), duplicateParentFields, childFields(),
			"主表关联键重复: id");

		List<GenTableColumn> duplicateChildFields = new ArrayList<>(childFields());
		duplicateChildFields.add(field("ORDER_ID", "duplicateOrderId", "Long", false, false));
		assertInvalidRelationship(parentChildTable(), parentFields(), duplicateChildFields,
			"子表关联键重复: order_id");
	}

	@Test
	void rejectsParentAndChildGeneratedClassNameConflict() {
		GenTable table = parentChildTable();
		table.setClassName("PurchaseOrderItem");

		assertInvalidRelationship(table, "父子表生成类名冲突: PurchaseOrderItem");
	}

	@Test
	void renderedParentChildBundleCompilesAndEnforcesAtomicOwnershipContract() throws Exception {
		List<Map<String, String>> preview = generator(parentChildTable()).preview(1L);

		assertThat(preview).hasSize(21);
		assertThat(preview).extracting(entry -> entry.get("codePath"))
			.anyMatch(path -> path.endsWith("/api/entity/PurchaseOrderItem.java"))
			.anyMatch(path -> path.endsWith("/api/dto/PurchaseOrderItemDTO.java"))
			.anyMatch(path -> path.endsWith("/api/vo/PurchaseOrderItemVO.java"))
			.anyMatch(path -> path.endsWith("/mapper/PurchaseOrderItemMapper.java"))
			.noneMatch(path -> path.endsWith("/PurchaseOrderItemController.java"));

		String service = codeEndingWith(preview, "/PurchaseOrderServiceImpl.java");
		assertThat(codeEndingWith(preview, "/PurchaseOrderMapper.java"))
				.contains("@DataScope(userAlias = \"\", userColumn = \"create_by\", creatorScope = true)");
		assertThat(codeEndingWith(preview, "/PurchaseOrderItemMapper.java"))
				.contains("@DataScope(userAlias = \"\", userColumn = \"create_by\", creatorScope = true)");
		assertThat(service)
			.contains("PermissionService permissionService")
			.contains("requirePermissions(\"orders_purchase_order_add\", \"orders_purchase_order_item_add\")")
			.contains("requirePermissions(\"orders_purchase_order_edit\", \"orders_purchase_order_item_edit\")")
			.contains("requirePermissions(\"orders_purchase_order_del\", \"orders_purchase_order_item_del\")")
			.contains("TenantContextHolder.get()")
			.contains("requireOwnedParent")
			.contains(".eq(PurchaseOrder::getTenantId, requireCurrentTenant())")
			.contains("PurchaseOrder::getId")
			.contains("PurchaseOrderItem::getOrderId")
			.contains(".eq(PurchaseOrderItem::getTenantId, requireCurrentTenant())")
			.contains("replaceChildren")
			.contains("childMapper.delete")
			.contains("childMapper.insert")
			.contains("child.setId(null)")
			.contains("requireWrite(save(parent), \"新增主表\")")
			.contains("PurchaseOrder current = requireOwnedParent(dto.getId())")
			.contains("parentUpdate.setId(current.getId())")
			.contains("requireWrite(updateById", "requireWrite(removeBatchByIds(ids), \"删除主表\")")
			.contains("if (childMapper.insert(child) != 1) throw new IllegalStateException")
			.contains("if (requestedChildren == null) throw new IllegalArgumentException")
			.contains("validateRequestedChildren")
			.contains("明细不存在或不属于当前主表")
			.contains("result.setOrderNo(entity.getOrderNo())")
			.doesNotContain("result.setOrderNo(maskText");

		String controller = codeEndingWith(preview, "/PurchaseOrderController.java");
		assertThat(controller)
			.contains("@HasPermission(\"orders_purchase_order_view\")")
			.contains("@HasPermission(\"orders_purchase_order_add\")")
			.contains("@HasPermission(\"orders_purchase_order_edit\")")
			.contains("@HasPermission(\"orders_purchase_order_del\")")
			.contains("@SysLog(\"新增采购订单\")")
			.contains("@SysLog(\"修改采购订单\")")
			.contains("@SysLog(\"删除采购订单\")");
		assertThat(codeEndingWith(preview, "/purchaseOrder.ts"))
			.contains("export interface PurchaseOrderItemForm")
			.contains("id?: string")
			.contains("orderId?: number")
			.contains("children: PurchaseOrderItemForm[]");
		assertThat(codeEndingWith(preview, "_menu.sql"))
			.contains("orders_purchase_order_item_add")
			.contains("orders_purchase_order_item_edit")
			.contains("orders_purchase_order_item_del");
		assertThat(codeEndingWith(preview, "/purchaseOrder/index.vue"))
			.contains("v-auth-all=\"['orders_purchase_order_add', 'orders_purchase_order_item_add']\"")
			.contains("v-auth-all=\"['orders_purchase_order_edit', 'orders_purchase_order_item_edit']\"")
			.contains("v-auth-all=\"['orders_purchase_order_del', 'orders_purchase_order_item_del']\"");
		assertThat(codeEndingWith(preview, "/purchaseOrder/form.vue"))
			.contains("form.children")
			.contains("addChild")
			.contains("removeChild")
			.contains("v-auth-all=\"writePermissions\"")
			.contains("children.${scope.$index}.sku");

		Class<?> serviceType = compileAndLoad(preview,
			"com.lotus.bixi.orders.service.impl.PurchaseOrderServiceImpl");
		assertRollbackTransaction(serviceType, "create",
			"com.lotus.bixi.orders.api.dto.PurchaseOrderCreateDTO");
		assertRollbackTransaction(serviceType, "update",
			"com.lotus.bixi.orders.api.dto.PurchaseOrderUpdateDTO");
		assertRollbackTransaction(serviceType, "delete", List.class.getName());
	}

	@Test
	void generatedWriteMethodsExecuteAtomicallyAndEnforceTenantIsolation() throws Exception {
		verifyExecutableWriteContract(generator(parentChildTable()).preview(1L));
	}

	@Test
	void generatedReplacementFailsIfTheDeletedChildIdIsReused() throws Exception {
		List<Map<String, String>> preview = generator(parentChildTable()).preview(1L);
		List<Map<String, String>> mutant = preview.stream().map(artifact -> {
			if (!artifact.get("codePath").endsWith("/PurchaseOrderServiceImpl.java")) return artifact;
			String original = artifact.get("code");
			String mutated = original.replace("\t\t\tchild.setId(null);\n", "");
			assertThat(mutated).as("mutation must remove child ID clearing").isNotEqualTo(original);
			Map<String, String> copy = new LinkedHashMap<>(artifact);
			copy.put("code", mutated);
			return copy;
		}).toList();
		Class<?> serviceType = compileAndLoad(mutant,
			"com.lotus.bixi.orders.service.impl.PurchaseOrderServiceImpl");
		GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(serviceType);
		try {
			TenantContextHolder.set(7L);
			long parentId = runtime.create("PO-MUTANT", List.of(new ChildInput("SKU-A", 1)));
			long childId = runtime.children(parentId).get(0).id();

			assertThatThrownBy(() -> runtime.update(parentId, "PO-MUTANT",
				List.of(new ChildInput(childId, parentId, "SKU-B", 2))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("新增明细失败");
		}
		finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
	}

	@Test
	void writesJavaRenderedFrontendFixtureForNodeSyntaxValidation() throws Exception {
		List<Map<String, String>> preview = generator(parentChildTable()).preview(1L);
		Path fixture = Files.createDirectories(Path.of(System.getProperty("basedir"), "target",
			"generated-parent-child-frontend"));
		Files.writeString(fixture.resolve("purchaseOrder.ts"), codeEndingWith(preview, "/purchaseOrder.ts"));
		Files.writeString(fixture.resolve("form.vue"), codeEndingWith(preview, "/purchaseOrder/form.vue"));
	}

	@Test
	void generatedWritesRequireBothParentAndChildPermissions() throws Exception {
		Class<?> serviceType = compileAndLoad(generator(parentChildTable()).preview(1L),
			"com.lotus.bixi.orders.service.impl.PurchaseOrderServiceImpl");
		GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(serviceType);
		try {
			TenantContextHolder.set(7L);
			runtime.grant("orders_purchase_order_add");
			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.create("PO-DENIED", List.of(new ChildInput("SKU-A", 1))))
				.isInstanceOf(AccessDeniedException.class);
			assertThat(runtime.writeAttempts()).isZero();

			runtime.grant("orders_purchase_order_add", "orders_purchase_order_item_add");
			long parentId = runtime.create("PO-ALLOWED", List.of(new ChildInput("SKU-A", 1)));

			runtime.grant("orders_purchase_order_edit");
			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.update(parentId, "PO-EDIT-DENIED",
				List.of(new ChildInput("SKU-B", 2))))
				.isInstanceOf(AccessDeniedException.class);
			assertThat(runtime.writeAttempts()).isZero();

			runtime.grant("orders_purchase_order_edit", "orders_purchase_order_item_edit");
			runtime.update(parentId, "PO-EDIT-ALLOWED", List.of(new ChildInput("SKU-B", 2)));

			runtime.grant("orders_purchase_order_del");
			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.delete(parentId)).isInstanceOf(AccessDeniedException.class);
			assertThat(runtime.writeAttempts()).isZero();

			runtime.grant("orders_purchase_order_del", "orders_purchase_order_item_del");
			runtime.delete(parentId);
			assertThat(runtime.state().parents()).isEmpty();
		}
		finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
	}

	@Test
	void generatedUpdateRejectsCrossParentChildIdentityAndRelationshipBeforeWriting() throws Exception {
		Class<?> serviceType = compileAndLoad(generator(parentChildTable()).preview(1L),
			"com.lotus.bixi.orders.service.impl.PurchaseOrderServiceImpl");
		GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(serviceType);
		try {
			TenantContextHolder.set(7L);
			long firstParent = runtime.create("PO-FIRST", List.of(new ChildInput("SKU-A", 1)));
			long secondParent = runtime.create("PO-SECOND", List.of(new ChildInput("SKU-B", 2)));
			ChildRow secondChild = runtime.children(secondParent).get(0);
			LedgerState beforeAttack = runtime.state();

			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.update(firstParent, "PO-HIJACK-ID", List.of(
				new ChildInput(secondChild.id(), firstParent, "SKU-X", 9))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("明细不存在或不属于当前主表");
			assertThat(runtime.writeAttempts()).isZero();
			assertThat(runtime.state()).isEqualTo(beforeAttack);

			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.update(firstParent, "PO-HIJACK-RELATION", List.of(
				new ChildInput(null, secondParent, "SKU-Y", 8))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("明细关系键不属于当前主表");
			assertThat(runtime.writeAttempts()).isZero();
			assertThat(runtime.state()).isEqualTo(beforeAttack);
		}
		finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
	}

	private void verifyExecutableWriteContract(List<Map<String, String>> preview) throws Exception {
		Class<?> serviceType = compileAndLoad(preview,
			"com.lotus.bixi.orders.service.impl.PurchaseOrderServiceImpl");
		GeneratedServiceRuntime runtime = new GeneratedServiceRuntime(serviceType);

		try {
			TenantContextHolder.set(7L);
			long parentId = runtime.create("PO-001", List.of(
				new ChildInput("SKU-A", 2), new ChildInput("SKU-B", 3)));
			assertThat(runtime.state().parents()).containsEntry(parentId,
				new ParentRow(parentId, "PO-001", 7L));
			assertThat(runtime.children(parentId)).extracting(ChildRow::sku)
				.containsExactly("SKU-A", "SKU-B");
			assertThat(runtime.children(parentId)).allSatisfy(child -> {
				assertThat(child.orderId()).isEqualTo(parentId);
				assertThat(child.tenantId()).isEqualTo(7L);
			});
			long replacedChildId = runtime.children(parentId).get(0).id();
			runtime.update(parentId, "PO-ID-REPLACED", List.of(
				new ChildInput(replacedChildId, parentId, "SKU-ID", 5)));
			assertThat(runtime.state().tombstones()).containsKey(replacedChildId);
			assertThat(runtime.children(parentId)).singleElement().satisfies(child -> {
				assertThat(child.id()).isNotEqualTo(replacedChildId);
				assertThat(child.sku()).isEqualTo("SKU-ID");
			});

			LedgerState beforeFailedCreate = runtime.state();
			runtime.failNthChildInsert(2);
			assertThatThrownBy(() -> runtime.create("PO-ROLLBACK", List.of(
				new ChildInput("SKU-X", 1), new ChildInput("SKU-Y", 1))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("新增明细失败");
			assertThat(runtime.state()).isEqualTo(beforeFailedCreate);

			runtime.update(parentId, "PO-UPDATED", List.of(new ChildInput("SKU-C", 9)));
			assertThat(runtime.state().parents().get(parentId).orderNo()).isEqualTo("PO-UPDATED");
			assertThat(runtime.children(parentId)).extracting(ChildRow::sku)
				.containsExactly("SKU-C");

			LedgerState beforeFailedUpdate = runtime.state();
			runtime.failNthChildInsert(1);
			assertThatThrownBy(() -> runtime.update(parentId, "PO-BROKEN",
				List.of(new ChildInput("SKU-Z", 4))))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("新增明细失败");
			assertThat(runtime.state()).isEqualTo(beforeFailedUpdate);

			TenantContextHolder.set(8L);
			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.update(parentId, "CROSS-TENANT",
				List.of(new ChildInput("SKU-T", 1))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("不存在或不属于当前租户");
			assertThat(runtime.writeAttempts()).isZero();
			assertThat(runtime.state()).isEqualTo(beforeFailedUpdate);

			runtime.resetWriteAttempts();
			assertThatThrownBy(() -> runtime.delete(parentId))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("不存在或不属于当前租户");
			assertThat(runtime.writeAttempts()).isZero();
			assertThat(runtime.state()).isEqualTo(beforeFailedUpdate);

			TenantContextHolder.set(7L);
			LedgerState beforeFailedDelete = runtime.state();
			runtime.resetWriteAttempts();
			runtime.failNextParentDelete();
			assertThatThrownBy(() -> runtime.delete(parentId))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("删除主表失败");
			assertThat(runtime.writeAttempts()).isEqualTo(2);
			assertThat(runtime.state()).isEqualTo(beforeFailedDelete);

			runtime.delete(parentId);
			assertThat(runtime.state().parents()).isEmpty();
			assertThat(runtime.state().children()).isEmpty();
		}
		finally {
			SecurityContextHolder.clearContext();
			TenantContextHolder.clear();
		}
	}

	private record ChildInput(Long id, Long orderId, String sku, Integer quantity) {

		private ChildInput(String sku, Integer quantity) {
			this(null, null, sku, quantity);
		}
	}

	private record ParentRow(Long id, String orderNo, Long tenantId) {
	}

	private record ChildRow(Long id, Long orderId, String sku, Integer quantity, Long tenantId) {
	}

	private record LedgerState(Map<Long, ParentRow> parents, Map<Long, ChildRow> children,
			Map<Long, ChildRow> tombstones) {
	}

	private static final class GeneratedServiceRuntime {

		private static final String PACKAGE = "com.lotus.bixi.orders";

		private final RuntimeLedger ledger;

		private final Object service;

		private final Method create;

		private final Method update;

		private final Method delete;

		private final Class<?> createDtoType;

		private final Class<?> updateDtoType;

		private final Class<?> childDtoType;

		private GeneratedServiceRuntime(Class<?> serviceType) throws Exception {
			ClassLoader loader = serviceType.getClassLoader();
			Class<?> parentEntityType = Class.forName(PACKAGE + ".api.entity.PurchaseOrder", true, loader);
			Class<?> childEntityType = Class.forName(PACKAGE + ".api.entity.PurchaseOrderItem", true, loader);
			Class<?> parentMapperType = Class.forName(PACKAGE + ".mapper.PurchaseOrderMapper", true, loader);
			Class<?> childMapperType = Class.forName(PACKAGE + ".mapper.PurchaseOrderItemMapper", true, loader);
			Class<?> serviceInterface = Class.forName(PACKAGE + ".api.service.PurchaseOrderService", true, loader);
			createDtoType = Class.forName(PACKAGE + ".api.dto.PurchaseOrderCreateDTO", true, loader);
			updateDtoType = Class.forName(PACKAGE + ".api.dto.PurchaseOrderUpdateDTO", true, loader);
			childDtoType = Class.forName(PACKAGE + ".api.dto.PurchaseOrderItemDTO", true, loader);

			initializeTableInfo(parentEntityType);
			initializeTableInfo(childEntityType);
			ledger = new RuntimeLedger(parentEntityType, childEntityType);
			Object parentMapper = mapperProxy(parentMapperType, ledger::invokeParentMapper);
			Object childMapper = mapperProxy(childMapperType, ledger::invokeChildMapper);
			Object target;
			try {
				target = serviceType.getDeclaredConstructor(childMapperType, PermissionService.class)
					.newInstance(childMapper, new PermissionService());
			}
			catch (NoSuchMethodException missingPermissionDependency) {
				target = serviceType.getDeclaredConstructor(childMapperType).newInstance(childMapper);
			}
			Field baseMapper = serviceType.getSuperclass().getSuperclass().getDeclaredField("baseMapper");
			baseMapper.setAccessible(true);
			baseMapper.set(target, parentMapper);

			ProxyFactory proxyFactory = new ProxyFactory(target);
			proxyFactory.addAdvice(new TransactionInterceptor(new LedgerTransactionManager(ledger),
				new AnnotationTransactionAttributeSource()));
			service = proxyFactory.getProxy(loader);
			create = serviceInterface.getMethod("create", createDtoType);
			update = serviceInterface.getMethod("update", updateDtoType);
			delete = serviceInterface.getMethod("delete", List.class);
			grant("orders_purchase_order_add", "orders_purchase_order_item_add",
				"orders_purchase_order_edit", "orders_purchase_order_item_edit",
				"orders_purchase_order_del", "orders_purchase_order_item_del");
		}

		private long create(String orderNo, List<ChildInput> children) throws Exception {
			Object dto = createDtoType.getDeclaredConstructor().newInstance();
			setProperty(dto, "OrderNo", orderNo);
			setProperty(dto, "Children", childDtos(children));
			invoke(create, dto);
			return ledger.parents.values().stream()
				.filter(parent -> orderNo.equals(parent.orderNo()))
				.mapToLong(ParentRow::id).findFirst().orElseThrow();
		}

		private void update(long id, String orderNo, List<ChildInput> children) throws Exception {
			Object dto = updateDtoType.getDeclaredConstructor().newInstance();
			setProperty(dto, "Id", id);
			setProperty(dto, "OrderNo", orderNo);
			setProperty(dto, "Children", childDtos(children));
			invoke(update, dto);
		}

		private void delete(long id) {
			invoke(delete, List.of(id));
		}

		private List<Object> childDtos(List<ChildInput> children) throws Exception {
			List<Object> result = new ArrayList<>();
			for (ChildInput child : children) {
				Object dto = childDtoType.getDeclaredConstructor().newInstance();
				setPropertyIfPresent(dto, "Id", child.id());
				setPropertyIfPresent(dto, "OrderId", child.orderId());
				setProperty(dto, "Sku", child.sku());
				setProperty(dto, "Quantity", child.quantity());
				result.add(dto);
			}
			return result;
		}

		private void invoke(Method method, Object argument) {
			try {
				method.invoke(service, argument);
			}
			catch (InvocationTargetException exception) {
				Throwable cause = exception.getCause();
				if (cause instanceof RuntimeException runtimeException) throw runtimeException;
				if (cause instanceof Error error) throw error;
				throw new IllegalStateException(cause);
			}
			catch (IllegalAccessException exception) {
				throw new IllegalStateException(exception);
			}
		}

		private LedgerState state() {
			return ledger.state();
		}

		private List<ChildRow> children(long parentId) {
			return ledger.children.values().stream().filter(child -> child.orderId() == parentId)
				.sorted((left, right) -> Long.compare(left.id(), right.id())).toList();
		}

		private void failNthChildInsert(int nth) {
			ledger.failNthChildInsert(nth);
		}

		private void failNextParentDelete() {
			ledger.failNextParentDelete = true;
		}

		private void resetWriteAttempts() {
			ledger.writeAttempts = 0;
		}

		private int writeAttempts() {
			return ledger.writeAttempts;
		}

		private void grant(String... permissions) {
			List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(permissions)
				.map(SimpleGrantedAuthority::new).toList();
			SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken("generator-test", "n/a", authorities));
		}

		private static Object mapperProxy(Class<?> mapperType, java.lang.reflect.InvocationHandler handler) {
			return Proxy.newProxyInstance(mapperType.getClassLoader(), new Class<?>[]{mapperType}, handler);
		}

		private static void initializeTableInfo(Class<?> entityType) {
			MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(),
				entityType.getName());
			TableInfoHelper.initTableInfo(assistant, entityType);
		}
	}

	private static final class RuntimeLedger {

		private final Class<?> parentEntityType;

		private final Class<?> childEntityType;

		private final Map<Long, ParentRow> parents = new LinkedHashMap<>();

		private final Map<Long, ChildRow> children = new LinkedHashMap<>();

		private final Map<Long, ChildRow> tombstones = new LinkedHashMap<>();

		private long nextParentId = 100L;

		private long nextChildId = 1000L;

		private int childInsertAttempts;

		private int failChildInsertAttempt = -1;

		private boolean failNextParentDelete;

		private int writeAttempts;

		private RuntimeLedger(Class<?> parentEntityType, Class<?> childEntityType) {
			this.parentEntityType = parentEntityType;
			this.childEntityType = childEntityType;
		}

		private Object invokeParentMapper(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
			return switch (method.getName()) {
				case "insert" -> insertParent(args[0]);
				case "updateById" -> updateParent(args[0]);
				case "selectOne" -> selectParent(args[0]);
				case "deleteByIds" -> deleteParents((Collection<?>) args[0]);
				default -> throw new UnsupportedOperationException("Unexpected parent mapper call: " + method);
			};
		}

		private Object invokeChildMapper(Object proxy, Method method, Object[] args) throws Throwable {
			if (method.getDeclaringClass() == Object.class) return objectMethod(proxy, method, args);
			return switch (method.getName()) {
				case "insert" -> insertChild(args[0]);
				case "delete" -> deleteChildren(args[0]);
				case "selectOne" -> selectChild(args[0]);
				default -> throw new UnsupportedOperationException("Unexpected child mapper call: " + method);
			};
		}

		private int insertParent(Object entity) throws Exception {
			writeAttempts++;
			Long id = (Long) getProperty(entity, "Id");
			if (id == null) {
				id = nextParentId++;
				setProperty(entity, "Id", id);
			}
			Long tenantId = (Long) getProperty(entity, "TenantId");
			if (tenantId == null) {
				tenantId = TenantContextHolder.get();
				setProperty(entity, "TenantId", tenantId);
			}
			parents.put(id, new ParentRow(id, (String) getProperty(entity, "OrderNo"), tenantId));
			return 1;
		}

		private int updateParent(Object entity) throws Exception {
			writeAttempts++;
			Long id = (Long) getProperty(entity, "Id");
			ParentRow current = parents.get(id);
			if (current == null || !Objects.equals(current.tenantId(), TenantContextHolder.get())) return 0;
			parents.put(id, new ParentRow(id, (String) getProperty(entity, "OrderNo"), current.tenantId()));
			return 1;
		}

		private Object selectParent(Object wrapper) throws Exception {
			Long id = number(wrapperParameter(wrapper, 1));
			Long tenantId = number(wrapperParameter(wrapper, 2));
			ParentRow row = parents.get(id);
			if (row == null || !Objects.equals(row.tenantId(), tenantId)) return null;
			Object entity = parentEntityType.getDeclaredConstructor().newInstance();
			setProperty(entity, "Id", row.id());
			setProperty(entity, "OrderNo", row.orderNo());
			setProperty(entity, "TenantId", row.tenantId());
			return entity;
		}

		private int deleteParents(Collection<?> ids) {
			writeAttempts++;
			if (failNextParentDelete) {
				failNextParentDelete = false;
				return 0;
			}
			int deleted = 0;
			for (Object value : ids) {
				Long id = number(value);
				ParentRow row = parents.get(id);
				if (row != null && Objects.equals(row.tenantId(), TenantContextHolder.get())) {
					parents.remove(id);
					deleted++;
				}
			}
			return deleted;
		}

		private int insertChild(Object entity) throws Exception {
			writeAttempts++;
			int attempt = ++childInsertAttempts;
			if (attempt == failChildInsertAttempt) {
				failChildInsertAttempt = -1;
				return 0;
			}
			Long id = (Long) getProperty(entity, "Id");
			if (id == null) {
				do {
					id = nextChildId++;
				}
				while (children.containsKey(id) || tombstones.containsKey(id));
				setProperty(entity, "Id", id);
			}
			else if (children.containsKey(id) || tombstones.containsKey(id)) {
				return 0;
			}
			children.put(id, new ChildRow(id, (Long) getProperty(entity, "OrderId"),
				(String) getProperty(entity, "Sku"), (Integer) getProperty(entity, "Quantity"),
				(Long) getProperty(entity, "TenantId")));
			return 1;
		}

		private int deleteChildren(Object wrapper) throws Exception {
			writeAttempts++;
			Long orderId = number(wrapperParameter(wrapper, 1));
			Long tenantId = number(wrapperParameter(wrapper, 2));
			List<Long> matching = children.values().stream()
				.filter(child -> Objects.equals(child.orderId(), orderId)
					&& Objects.equals(child.tenantId(), tenantId))
				.map(ChildRow::id).toList();
			matching.forEach(id -> tombstones.put(id, children.remove(id)));
			return matching.size();
		}

		private Object selectChild(Object wrapper) throws Exception {
			Long id = number(wrapperParameter(wrapper, 1));
			Long orderId = number(wrapperParameter(wrapper, 2));
			Long tenantId = number(wrapperParameter(wrapper, 3));
			ChildRow row = children.get(id);
			if (row == null || !Objects.equals(row.orderId(), orderId)
					|| !Objects.equals(row.tenantId(), tenantId)) return null;
			Object entity = childEntityType.getDeclaredConstructor().newInstance();
			setProperty(entity, "Id", row.id());
			setProperty(entity, "OrderId", row.orderId());
			setProperty(entity, "Sku", row.sku());
			setProperty(entity, "Quantity", row.quantity());
			setProperty(entity, "TenantId", row.tenantId());
			return entity;
		}

		private void failNthChildInsert(int nth) {
			if (nth < 1) throw new IllegalArgumentException("nth must be positive");
			failChildInsertAttempt = childInsertAttempts + nth;
		}

		private LedgerState state() {
			return new LedgerState(Map.copyOf(parents), Map.copyOf(children), Map.copyOf(tombstones));
		}

		private void restore(LedgerState state) {
			parents.clear();
			parents.putAll(state.parents());
			children.clear();
			children.putAll(state.children());
			tombstones.clear();
			tombstones.putAll(state.tombstones());
		}
	}

	private static final class LedgerTransactionManager extends AbstractPlatformTransactionManager {

		private final RuntimeLedger ledger;

		private LedgerTransactionManager(RuntimeLedger ledger) {
			this.ledger = ledger;
		}

		@Override
		protected Object doGetTransaction() {
			return new LedgerTransaction();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			((LedgerTransaction) transaction).before = ledger.state();
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			ledger.restore(((LedgerTransaction) status.getTransaction()).before);
		}
	}

	private static final class LedgerTransaction {

		private LedgerState before;
	}

	private static Object objectMethod(Object proxy, Method method, Object[] args) {
		return switch (method.getName()) {
			case "toString" -> "GeneratedMapperProxy";
			case "hashCode" -> System.identityHashCode(proxy);
			case "equals" -> proxy == args[0];
			default -> throw new UnsupportedOperationException(method.toString());
		};
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> wrapperParameters(Object wrapper) throws Exception {
		wrapper.getClass().getMethod("getSqlSegment").invoke(wrapper);
		return (Map<String, Object>) wrapper.getClass()
			.getMethod("getParamNameValuePairs").invoke(wrapper);
	}

	private static Object wrapperParameter(Object wrapper, int position) throws Exception {
		return wrapperParameters(wrapper).get("MPGENVAL" + position);
	}

	private static Long number(Object value) {
		return value == null ? null : ((Number) value).longValue();
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

	private static void setPropertyIfPresent(Object target, String property, Object value) throws Exception {
		if (value == null) return;
		Method setter = java.util.Arrays.stream(target.getClass().getMethods())
			.filter(method -> method.getName().equals("set" + property) && method.getParameterCount() == 1)
			.findFirst().orElse(null);
		if (setter != null) setter.invoke(target, value);
	}

	private void assertInvalidRelationship(GenTable table, String message) {
		assertThatThrownBy(() -> generator(table).preview(1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(message);
	}

	private void assertInvalidRelationship(GenTable table, List<GenTableColumn> parentColumns,
			List<GenTableColumn> childColumns, String message) {
		assertThatThrownBy(() -> generator(table, parentColumns, childColumns).preview(1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining(message);
	}

	@SuppressWarnings("unchecked")
	private GeneratorServiceImpl generator(GenTable table) {
		return generator(table, parentFields(), childFields());
	}

	@SuppressWarnings("unchecked")
	private GeneratorServiceImpl generator(GenTable table, List<GenTableColumn> parentColumns,
			List<GenTableColumn> childColumns) {
		BixiGeneratorDefaultProperties properties = new BixiGeneratorDefaultProperties();
		properties.setProjectRoot(projectRoot.toString());
		properties.setApiPath("api");
		properties.setBackendPath("biz");
		properties.setFrontendPath("ui");
		properties.setAllowedOutputRoots(List.of("api", "biz", "ui", "bixi-project-documents/sql"));

		GenTableColumnService columns = mock(GenTableColumnService.class);
		@SuppressWarnings("unchecked")
		LambdaQueryChainWrapper<GenTableColumn> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
		when(columns.lambdaQuery()).thenReturn(query);
		when(query.eq(any(SFunction.class), any())).thenReturn(query);
		when(query.orderByAsc(any(SFunction.class))).thenReturn(query);
		AtomicInteger calls = new AtomicInteger();
		when(query.list()).thenAnswer(ignored -> calls.getAndIncrement() == 0 ? parentColumns : childColumns);

		GenFieldTypeService fieldTypes = mock(GenFieldTypeService.class);
		when(fieldTypes.getPackageByTableId("master", "purchase_order")).thenReturn(Set.of());
		when(fieldTypes.getPackageByTableId("master", "purchase_order_item")).thenReturn(Set.of());
		GenTableService tables = mock(GenTableService.class);
		when(tables.getById(1L)).thenReturn(table);

		return new GeneratorServiceImpl(properties, columns, fieldTypes, tables,
			mock(GenGroupService.class), new BuiltInTemplateCatalog());
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
		URLClassLoader loader = new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},
			getClass().getClassLoader());
		return Class.forName(className, true, loader);
	}

	private void assertRollbackTransaction(Class<?> serviceType, String methodName,
			String parameterType) throws Exception {
		Class<?> parameter = List.class.getName().equals(parameterType)
			? List.class
			: Class.forName(parameterType, true, serviceType.getClassLoader());
		Transactional transactional = serviceType.getMethod(methodName, parameter)
			.getAnnotation(Transactional.class);
		assertThat(transactional).as(methodName + " transaction").isNotNull();
		assertThat(transactional.rollbackFor()).contains(Exception.class);
	}

	private static String codeEndingWith(List<Map<String, String>> preview, String suffix) {
		return preview.stream().filter(entry -> entry.get("codePath").endsWith(suffix))
			.findFirst().orElseThrow().get("code");
	}

	private static GenTable parentChildTable() {
		GenTable table = parentTable();
		table.setChildTableName("purchase_order_item");
		table.setMainField("id");
		table.setChildField("order_id");
		return table;
	}

	private static GenTable parentTable() {
		GenTable table = new GenTable();
		table.setId(1L);
		table.setDsName("master");
		table.setDbType("MySQL");
		table.setTableName("purchase_order");
		table.setClassName("PurchaseOrder");
		table.setTableComment("采购订单");
		table.setPackageName("com.lotus.bixi");
		table.setVersion("1.0.0");
		table.setModuleName("orders");
		table.setFunctionName("purchaseOrder");
		table.setFormLayout(2);
		table.setStyle(BuiltInTemplateCatalog.DEFAULT_GROUP_ID);
		table.setAuthor("bixi");
		return table;
	}

	private static List<GenTableColumn> parentFields() {
		return List.of(
			field("id", "id", "Long", true, false),
			field("order_no", "orderNo", "String", false, true),
			field("tenant_id", "tenantId", "Long", false, false),
			field("create_time", "createTime", "LocalDateTime", false, false)
		);
	}

	private static List<GenTableColumn> childFields() {
		return List.of(
			field("id", "id", "Long", true, false),
			field("order_id", "orderId", "Long", false, false),
			field("sku", "sku", "String", false, true),
			field("quantity", "quantity", "Integer", false, true),
			field("tenant_id", "tenantId", "Long", false, false)
		);
	}

	private static GenTableColumn field(String name, String attrName, String attrType,
			boolean primary, boolean form) {
		GenTableColumn field = new GenTableColumn();
		field.setFieldName(name);
		field.setAttrName(attrName);
		field.setAttrType(attrType);
		field.setFieldComment(name);
		field.setPrimaryPk(primary ? "1" : "0");
		field.setFormItem(form ? "1" : "0");
		field.setFormRequired("sku".equals(name) ? "1" : "0");
		field.setGridItem(form ? "1" : "0");
		field.setQueryItem("0");
		field.setQueryType("=");
		field.setFormType("text");
		return field;
	}
}
