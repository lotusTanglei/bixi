package com.lotus.bixi.acceptance.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamCreateDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportResult;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamImportRowError;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamQueryDTO;
import com.lotus.bixi.acceptance.api.dto.SysPublicParamUpdateDTO;
import com.lotus.bixi.acceptance.api.entity.SysPublicParam;
import com.lotus.bixi.acceptance.api.vo.SysPublicParamVO;
import com.lotus.bixi.acceptance.api.vo.SysPublicParamExportVO;
import com.lotus.bixi.acceptance.mapper.SysPublicParamMapper;
import com.lotus.bixi.acceptance.api.service.SysPublicParamService;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.springframework.dao.DuplicateKeyException;
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

@Service("acceptanceSysPublicParamService")
public class SysPublicParamServiceImpl extends ServiceImpl<SysPublicParamMapper, SysPublicParam> implements SysPublicParamService {
	private static final int MAX_IMPORT_ROWS = 1000;
	private static final int MAX_IMPORT_ERRORS = SysPublicParamImportResult.MAX_ERRORS;
	private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Override
	public IPage<SysPublicParamVO> pageSysPublicParam(Page<SysPublicParam> page, SysPublicParamQueryDTO query) {
		LambdaQueryWrapper<SysPublicParam> wrapper = queryWrapper(query);
		wrapper.orderByDesc(SysPublicParam::getCreateTime);
		return page(page, wrapper).convert(entity -> BeanUtil.copyProperties(entity, SysPublicParamVO.class));
	}

	private LambdaQueryWrapper<SysPublicParam> queryWrapper(SysPublicParamQueryDTO query) {
		if (query == null) query = new SysPublicParamQueryDTO();
		return Wrappers.<SysPublicParam>lambdaQuery()
			.eq(SysPublicParam::getTenantId, requireCurrentTenant())
			.like(StrUtil.isNotBlank(query.getName()), SysPublicParam::getName, query.getName())
			.like(StrUtil.isNotBlank(query.getKey()), SysPublicParam::getKey, query.getKey())
			.eq(StrUtil.isNotBlank(query.getType()), SysPublicParam::getType, query.getType())
			.eq(StrUtil.isNotBlank(query.getSystemFlag()), SysPublicParam::getSystemFlag, query.getSystemFlag());
	}

	@Override
	public SysPublicParamVO details(Long id) {
		SysPublicParam entity = requireOwned(id);
		return BeanUtil.copyProperties(entity, SysPublicParamVO.class);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean create(SysPublicParamCreateDTO dto) {
		SysPublicParam entity = BeanUtil.copyProperties(dto, SysPublicParam.class);
		entity.setTenantId(requireCurrentTenant());
		return save(entity);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean update(SysPublicParamUpdateDTO dto) {
		SysPublicParam current = requireOwned(dto.getId());
		SysPublicParam update = BeanUtil.copyProperties(dto, SysPublicParam.class);
		update.setId(current.getId());
		update.setTenantId(current.getTenantId());
		return updateById(update);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean delete(List<Long> ids) {
		if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("至少选择一条数据");
		List<Long> distinctIds = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
		if (distinctIds.isEmpty()) throw new IllegalArgumentException("至少选择一条数据");
		distinctIds.forEach(this::requireOwned);
		return removeBatchByIds(distinctIds);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SysPublicParamImportResult importRows(List<SysPublicParamImportDTO> rows) {
		requireCurrentTenant();
		if (rows == null || rows.isEmpty()) {
			return SysPublicParamImportResult.failure("EMPTY_IMPORT", 0,
				List.of(new SysPublicParamImportRowError(1, List.of("导入文件不包含数据行"))));
		}
		if (rows.size() > MAX_IMPORT_ROWS) {
			return SysPublicParamImportResult.failure("ROW_LIMIT_EXCEEDED", rows.size(),
				List.of(new SysPublicParamImportRowError(MAX_IMPORT_ROWS + 1,
					List.of("导入行数不能超过" + MAX_IMPORT_ROWS))));
		}

		List<SysPublicParamImportRowError> errors = new ArrayList<>();
		Set<String> fingerprints = new HashSet<>();
		for (int index = 0; index < rows.size(); index++) {
			SysPublicParamImportDTO row = rows.get(index);
			int rowNumber = index + 2;
			if (row == null) {
				addError(errors, rowNumber, List.of("数据行不能为空"));
				continue;
			}
			List<String> violations = validator.validate(row).stream()
				.map(ConstraintViolation::getMessage).filter(Objects::nonNull).distinct().sorted()
				.limit(SysPublicParamImportRowError.MAX_MESSAGES).toList();
			if (!violations.isEmpty()) addError(errors, rowNumber, violations);
			if (!fingerprints.add(fingerprint(row))) {
				addError(errors, rowNumber, List.of("上传文件内存在重复数据行"));
			}
		}
		if (!errors.isEmpty()) {
			return SysPublicParamImportResult.failure("VALIDATION_FAILED", rows.size(), errors);
		}

		int currentRow = 2;
		try {
			for (SysPublicParamImportDTO row : rows) {
				SysPublicParam entity = new SysPublicParam();
				entity.setName(row.getName());
				entity.setKey(row.getKey());
				entity.setValue(row.getValue());
				entity.setValidateCode(row.getValidateCode());
				entity.setType(row.getType());
				entity.setSystemFlag(row.getSystemFlag());
				entity.setSn(row.getSn());
				entity.setTenantId(requireCurrentTenant());
				if (baseMapper.insert(entity) != 1) throw new ImportWriteException(currentRow);
				currentRow++;
			}
		}
		catch (DuplicateKeyException collision) {
			markRollbackOnly();
			return SysPublicParamImportResult.failure("DUPLICATE_KEY", rows.size(),
				List.of(new SysPublicParamImportRowError(currentRow, List.of("数据唯一性冲突"))));
		}
		catch (RuntimeException failure) {
			markRollbackOnly();
			return SysPublicParamImportResult.failure("WRITE_FAILED", rows.size(),
				List.of(new SysPublicParamImportRowError(currentRow, List.of("数据写入失败"))));
		}
		return SysPublicParamImportResult.success(rows.size());
	}

	@Override
	public List<SysPublicParamExportVO> exportRows(SysPublicParamQueryDTO query) {
		return list(queryWrapper(query)).stream().map(this::toExportVO).toList();
	}

	private SysPublicParamExportVO toExportVO(SysPublicParam entity) {
		SysPublicParamExportVO result = new SysPublicParamExportVO();
		result.setName(entity.getName());
		result.setKey(entity.getKey());
		result.setValue(entity.getValue());
		result.setValidateCode(entity.getValidateCode());
		result.setType(entity.getType());
		result.setSystemFlag(entity.getSystemFlag());
		result.setSn(entity.getSn());
		return result;
	}

	private static void addError(List<SysPublicParamImportRowError> errors, int rowNumber,
			List<String> messages) {
		if (errors.size() < MAX_IMPORT_ERRORS) {
			errors.add(new SysPublicParamImportRowError(rowNumber, messages));
		}
	}

	private static String fingerprint(SysPublicParamImportDTO row) {
		StringBuilder normalized = new StringBuilder();
		appendFingerprint(normalized, row.getName());
		appendFingerprint(normalized, row.getKey());
		appendFingerprint(normalized, row.getValue());
		appendFingerprint(normalized, row.getValidateCode());
		appendFingerprint(normalized, row.getType());
		appendFingerprint(normalized, row.getSystemFlag());
		appendFingerprint(normalized, row.getSn());
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

	private SysPublicParam requireOwned(Long id) {
		if (id == null) throw new IllegalArgumentException("公共参数配置表ID不能为空");
		Long tenantId = requireCurrentTenant();
		SysPublicParam entity = getOne(Wrappers.<SysPublicParam>lambdaQuery()
			.eq(SysPublicParam::getId, id)
			.eq(SysPublicParam::getTenantId, tenantId));
		if (entity == null || !tenantId.equals(entity.getTenantId())) {
			throw new IllegalArgumentException("公共参数配置表不存在或不属于当前租户");
		}
		return entity;
	}

	private static Long requireCurrentTenant() {
		Long tenantId = TenantContextHolder.get();
		if (tenantId == null) throw new IllegalStateException("当前租户不能为空");
		return tenantId;
	}
}
