package com.lotus.bixi.acceptance.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.security.component.PermissionService;
import com.lotus.bixi.acceptance.api.dto.SysDictItemDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictCreateDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictImportDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictImportResult;
import com.lotus.bixi.acceptance.api.dto.SysDictImportRowError;
import com.lotus.bixi.acceptance.api.dto.SysDictQueryDTO;
import com.lotus.bixi.acceptance.api.dto.SysDictUpdateDTO;
import com.lotus.bixi.acceptance.api.entity.SysDictItem;
import com.lotus.bixi.acceptance.api.entity.SysDict;
import com.lotus.bixi.acceptance.api.vo.SysDictItemVO;
import com.lotus.bixi.acceptance.api.vo.SysDictVO;
import com.lotus.bixi.acceptance.api.vo.SysDictExportVO;
import com.lotus.bixi.acceptance.mapper.SysDictItemMapper;
import com.lotus.bixi.acceptance.mapper.SysDictMapper;
import com.lotus.bixi.acceptance.api.service.SysDictService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service("acceptanceSysDictService")
@RequiredArgsConstructor
public class SysDictServiceImpl extends ServiceImpl<SysDictMapper, SysDict> implements SysDictService {
	private static final int MAX_IMPORT_ROWS = 1000;
	private static final int MAX_IMPORT_ERRORS = SysDictImportResult.MAX_ERRORS;
	private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private final SysDictItemMapper childMapper;
	private final PermissionService permissionService;

	@Override
	public IPage<SysDictVO> pageSysDict(Page<SysDict> page, SysDictQueryDTO query) {
		LambdaQueryWrapper<SysDict> wrapper = queryWrapper(query);
		wrapper.orderByDesc(SysDict::getCreateTime);
		return page(page, wrapper).convert(entity -> BeanUtil.copyProperties(entity, SysDictVO.class));
	}

	private LambdaQueryWrapper<SysDict> queryWrapper(SysDictQueryDTO query) {
		if (query == null) query = new SysDictQueryDTO();
		return Wrappers.<SysDict>lambdaQuery()
			.eq(SysDict::getTenantId, requireCurrentTenant())
			.like(StrUtil.isNotBlank(query.getType()), SysDict::getType, query.getType())
			.like(StrUtil.isNotBlank(query.getName()), SysDict::getName, query.getName())
			.eq(StrUtil.isNotBlank(query.getSystemFlag()), SysDict::getSystemFlag, query.getSystemFlag());
	}

	@Override
	public SysDictVO details(Long id) {
		SysDict parent = requireOwnedParent(id);
		SysDictVO result = BeanUtil.copyProperties(parent, SysDictVO.class);
		List<SysDictItem> children = childMapper.selectList(Wrappers.<SysDictItem>lambdaQuery()
			.eq(SysDictItem::getDictId, parent.getId())
			.eq(SysDictItem::getTenantId, requireCurrentTenant())
			.orderByAsc(SysDictItem::getId));
		result.setChildren(children.stream()
			.map(child -> BeanUtil.copyProperties(child, SysDictItemVO.class)).toList());
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean create(SysDictCreateDTO dto) {
		requirePermissions("acceptance_dict_aggregate_add", "acceptance_sys_dict_item_add");
		validateNewChildren(dto.getChildren());
		SysDict parent = BeanUtil.copyProperties(dto, SysDict.class);
		parent.setTenantId(requireCurrentTenant());
		requireWrite(save(parent), "新增主表");
		replaceChildren(parent, dto.getChildren());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean update(SysDictUpdateDTO dto) {
		requirePermissions("acceptance_dict_aggregate_edit", "acceptance_sys_dict_item_edit");
		SysDict current = requireOwnedParent(dto.getId());
		validateRequestedChildren(current, dto.getChildren());
		SysDict parentUpdate = BeanUtil.copyProperties(dto, SysDict.class);
		parentUpdate.setId(current.getId());
		parentUpdate.setTenantId(current.getTenantId());
		requireWrite(updateById(parentUpdate), "修改主表");
		replaceChildren(requireOwnedParent(dto.getId()), dto.getChildren());
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean delete(List<Long> ids) {
		requirePermissions("acceptance_dict_aggregate_del", "acceptance_sys_dict_item_del");
		if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("至少选择一条数据");
		List<SysDict> parents = ids.stream().distinct().map(this::requireOwnedParent).toList();
		for (SysDict parent : parents) {
			deleteChildren(parent);
		}
		requireWrite(removeBatchByIds(ids), "删除主表");
		return true;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SysDictImportResult importRows(List<SysDictImportDTO> rows) {
		requireCurrentTenant();
		if (rows == null || rows.isEmpty()) {
			return SysDictImportResult.failure("EMPTY_IMPORT", 0,
				List.of(new SysDictImportRowError(1, List.of("导入文件不包含数据行"))));
		}
		if (rows.size() > MAX_IMPORT_ROWS) {
			return SysDictImportResult.failure("ROW_LIMIT_EXCEEDED", rows.size(),
				List.of(new SysDictImportRowError(MAX_IMPORT_ROWS + 1,
					List.of("导入行数不能超过" + MAX_IMPORT_ROWS))));
		}
		List<SysDictImportRowError> errors = new ArrayList<>();
		Set<String> fingerprints = new HashSet<>();
		for (int index = 0; index < rows.size(); index++) {
			SysDictImportDTO row = rows.get(index);
			int rowNumber = index + 2;
			if (row == null) {
				addError(errors, rowNumber, List.of("数据行不能为空"));
				continue;
			}
			List<String> violations = validator.validate(row).stream()
				.map(ConstraintViolation::getMessage).filter(Objects::nonNull).distinct().sorted()
				.limit(SysDictImportRowError.MAX_MESSAGES).toList();
			if (!violations.isEmpty()) addError(errors, rowNumber, violations);
			if (!fingerprints.add(fingerprint(row))) {
				addError(errors, rowNumber, List.of("上传文件内存在重复数据行"));
			}
		}
		if (!errors.isEmpty()) return SysDictImportResult.failure("VALIDATION_FAILED", rows.size(), errors);

		int currentRow = 2;
		try {
			for (SysDictImportDTO row : rows) {
				SysDict entity = new SysDict();
				entity.setType(row.getType());
				entity.setName(row.getName());
				entity.setDescription(row.getDescription());
				entity.setSn(row.getSn());
				entity.setSystemFlag(row.getSystemFlag());
				entity.setTenantId(requireCurrentTenant());
				if (baseMapper.insert(entity) != 1) throw new ImportWriteException(currentRow);
				currentRow++;
			}
		}
		catch (DuplicateKeyException collision) {
			markRollbackOnly();
			return SysDictImportResult.failure("DUPLICATE_KEY", rows.size(),
				List.of(new SysDictImportRowError(currentRow, List.of("数据唯一性冲突"))));
		}
		catch (RuntimeException failure) {
			markRollbackOnly();
			return SysDictImportResult.failure("WRITE_FAILED", rows.size(),
				List.of(new SysDictImportRowError(currentRow, List.of("数据写入失败"))));
		}
		return SysDictImportResult.success(rows.size());
	}

	@Override
	public List<SysDictExportVO> exportRows(SysDictQueryDTO query) {
		return list(queryWrapper(query)).stream().map(this::toExportVO).toList();
	}

	private SysDictExportVO toExportVO(SysDict entity) {
		SysDictExportVO result = new SysDictExportVO();
		result.setType(entity.getType());
		result.setName(entity.getName());
		result.setDescription(entity.getDescription());
		result.setSn(entity.getSn());
		result.setSystemFlag(entity.getSystemFlag());
		return result;
	}

	private static void addError(List<SysDictImportRowError> errors, int rowNumber, List<String> messages) {
		if (errors.size() < MAX_IMPORT_ERRORS) errors.add(new SysDictImportRowError(rowNumber, messages));
	}

	private static String fingerprint(SysDictImportDTO row) {
		StringBuilder normalized = new StringBuilder();
		appendFingerprint(normalized, row.getType());
		appendFingerprint(normalized, row.getName());
		appendFingerprint(normalized, row.getDescription());
		appendFingerprint(normalized, row.getSn());
		appendFingerprint(normalized, row.getSystemFlag());
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
				.digest(normalized.toString().getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}

	private static void appendFingerprint(StringBuilder target, Object value) {
		String normalized = value == null ? "" : Normalizer.normalize(value.toString().strip(),
			Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
		target.append(normalized.length()).append(':').append(normalized).append(';');
	}

	private static String maskEmail(String value) {
		if (StrUtil.isBlank(value)) return value;
		String text = value.strip();
		int separator = text.indexOf('@');
		if (separator <= 0) return maskText(text);
		return text.substring(0, 1) + "***" + text.substring(separator);
	}

	private static String maskPhone(String value) {
		if (StrUtil.isBlank(value)) return value;
		String text = value.strip();
		if (text.length() <= 7) return maskText(text);
		return text.substring(0, 3) + "****" + text.substring(text.length() - 4);
	}

	private static String maskIdentity(String value) {
		if (StrUtil.isBlank(value)) return value;
		String text = value.strip();
		if (text.length() <= 6) return maskText(text);
		return text.substring(0, 2) + "********" + text.substring(text.length() - 4);
	}

	private static String maskText(String value) {
		if (StrUtil.isBlank(value)) return value;
		String text = value.strip();
		if (text.length() == 1) return "*";
		return text.substring(0, 1) + "***" + text.substring(text.length() - 1);
	}

	private static void markRollbackOnly() {
		TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
	}

	private static final class ImportWriteException extends RuntimeException {
		private ImportWriteException(int rowNumber) {
			super("import write failed at row " + rowNumber);
		}
	}

	private SysDict requireOwnedParent(Long id) {
		if (id == null) throw new IllegalArgumentException("字典表ID不能为空");
		SysDict parent = getOne(Wrappers.<SysDict>lambdaQuery()
			.eq(SysDict::getId, id)
			.eq(SysDict::getTenantId, requireCurrentTenant())
		);
		if (parent == null) throw new IllegalArgumentException("字典表不存在或不属于当前租户");
		if (!Objects.equals(parent.getTenantId(), requireCurrentTenant())) {
			throw new IllegalArgumentException("字典表不属于当前租户");
		}
		return parent;
	}

	private void validateNewChildren(List<SysDictItemDTO> requestedChildren) {
		if (requestedChildren == null) throw new IllegalArgumentException("明细集合不能为空");
		for (SysDictItemDTO requested : requestedChildren) {
			if (requested == null) throw new IllegalArgumentException("明细不能为空");
			if (requested.getId() != null || requested.getDictId() != null) {
				throw new IllegalArgumentException("新增明细不能指定ID或关系键");
			}
		}
	}

	private void validateRequestedChildren(SysDict parent,
			List<SysDictItemDTO> requestedChildren) {
		if (requestedChildren == null) throw new IllegalArgumentException("明细集合不能为空");
		Object relationshipKey = parent.getId();
		if (relationshipKey == null) throw new IllegalArgumentException("主表关系键不能为空");
		Set<Long> requestedIds = new HashSet<>();
		for (SysDictItemDTO requested : requestedChildren) {
			if (requested == null) throw new IllegalArgumentException("明细不能为空");
			if (requested.getDictId() != null
					&& !Objects.equals(requested.getDictId(), relationshipKey)) {
				throw new IllegalArgumentException("明细关系键不属于当前主表");
			}
			if (requested.getId() == null) continue;
			if (!requestedIds.add(requested.getId())) throw new IllegalArgumentException("明细ID不能重复");
			SysDictItem existing = childMapper.selectOne(Wrappers.<SysDictItem>lambdaQuery()
				.eq(SysDictItem::getId, requested.getId())
				.eq(SysDictItem::getDictId, relationshipKey)
				.eq(SysDictItem::getTenantId, requireCurrentTenant())
			);
			if (existing == null || !Objects.equals(existing.getTenantId(), requireCurrentTenant())) {
				throw new IllegalArgumentException("明细不存在或不属于当前主表");
			}
		}
	}

	private void replaceChildren(SysDict parent,
			List<SysDictItemDTO> requestedChildren) {
		if (requestedChildren == null) throw new IllegalArgumentException("明细集合不能为空");
		Object relationshipKey = parent.getId();
		if (relationshipKey == null) throw new IllegalArgumentException("主表关系键不能为空");
		deleteChildren(parent);
		for (SysDictItemDTO requested : requestedChildren) {
			if (requested == null) throw new IllegalArgumentException("明细不能为空");
			SysDictItem child = BeanUtil.copyProperties(requested, SysDictItem.class);
			child.setId(null);
			child.setDictId(parent.getId());
			child.setTenantId(requireCurrentTenant());
			if (childMapper.insert(child) != 1) throw new IllegalStateException("新增明细失败");
		}
	}

	private void deleteChildren(SysDict parent) {
		Object relationshipKey = parent.getId();
		if (relationshipKey == null) throw new IllegalArgumentException("主表关系键不能为空");
		childMapper.delete(Wrappers.<SysDictItem>lambdaQuery()
			.eq(SysDictItem::getDictId, relationshipKey)
			.eq(SysDictItem::getTenantId, requireCurrentTenant())
		);
	}

	private static Long requireCurrentTenant() {
		Long tenantId = TenantContextHolder.get();
		if (tenantId == null) throw new IllegalStateException("当前租户不能为空");
		return tenantId;
	}

	private void requirePermissions(String parentPermission, String childPermission) {
		if (!permissionService.hasPermission(parentPermission) || !permissionService.hasPermission(childPermission)) {
			throw new AccessDeniedException("父子聚合写权限不足");
		}
	}

	private static void requireWrite(boolean written, String operation) {
		if (!written) throw new IllegalStateException(operation + "失败");
	}
}
