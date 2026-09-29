package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.common.core.exception.CheckedException;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.dto.TemplateUpdateResult;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.entity.GenTemplate;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import com.lotus.bixi.generator.mapper.GenGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateMapper;
import com.lotus.bixi.generator.service.GenTemplateService;
import com.lotus.bixi.generator.template.remote.TemplateUpdatePackage;
import com.lotus.bixi.generator.template.remote.TemplateUpdatePackageLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模板
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "generator", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GenTemplateServiceImpl extends ServiceImpl<GenTemplateMapper, GenTemplate>
		implements GenTemplateService {

	private final GenTemplateGroupMapper genTemplateGroupMapper;

	private final GenGroupMapper genGroupMapper;

	private final BixiGeneratorDefaultProperties defaultProperties;

	private final TemplateUpdatePackageLoader packageLoader;

	/**
	 * 在线更新
	 * @return {@link R }
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R<TemplateUpdateResult> onlineUpdate() {
		if (!defaultProperties.isAutoCheckVersion()) {
			throw new CheckedException("在线模板更新未启用");
		}
		TemplateUpdatePackageLoader.VerifiedManifest verified = packageLoader.inspect();
		String groupName = installedGroupName(verified.manifest().groupName(), verified.manifest().revision());
		if (groupExists(groupName)) {
			return R.ok(result(false, verified, groupName));
		}

		TemplateUpdatePackage update = packageLoader.load(verified);
		if (!verified.manifest().revision().equals(update.revision())
				|| !verified.digest().equals(update.manifestDigest())
				|| !verified.manifest().groupName().equals(update.groupName())) {
			throw new IllegalArgumentException("在线模板包身份与manifest不匹配");
		}
		if (!insertPackage(update, groupName)) {
			return R.ok(result(false, verified, groupName));
		}
		return R.ok(new TemplateUpdateResult(true, update.revision(), update.manifestDigest(),
				groupName, update.templates().size()));
	}

	/**
	 * 检查版本
	 * @return {@link R }
	 */
	public R<Boolean> checkVersion() {
		if (!defaultProperties.isAutoCheckVersion()) {
			return R.ok(true);
		}
		TemplateUpdatePackageLoader.VerifiedManifest verified = packageLoader.inspect();
		return R.ok(groupExists(installedGroupName(
				verified.manifest().groupName(), verified.manifest().revision())));
	}

	private boolean groupExists(String groupName) {
		return genGroupMapper.exists(Wrappers.<GenGroup>lambdaQuery()
				.eq(GenGroup::getGroupName, groupName));
	}

	private boolean insertPackage(TemplateUpdatePackage update, String groupName) {
		GenGroup genGroup = new GenGroup();
		genGroup.setGroupName(groupName);
		genGroup.setGroupDesc("revision=" + update.revision() + ";manifest=" + update.manifestDigest());
		try {
			requireInserted(genGroupMapper.insert(genGroup), "模板组");
		}
		catch (DuplicateKeyException concurrentInstall) {
			if (groupExistsCurrent(groupName)) return false;
			throw concurrentInstall;
		}
		if (genGroup.getId() == null) throw new CheckedException("模板组ID生成失败");

		for (TemplateUpdatePackage.TemplateFile file : update.templates()) {
			GenTemplate genTemplate = new GenTemplate();
			genTemplate.setTemplateName(file.templateName() + "@" + update.revision());
			genTemplate.setTemplateDesc("revision=" + update.revision() + ";sha256=" + file.sha256());
			genTemplate.setTemplateCode(file.content());
			genTemplate.setGeneratorPath(file.generatorPath());
			requireInserted(baseMapper.insert(genTemplate), "模板");
			if (genTemplate.getId() == null) throw new CheckedException("模板ID生成失败");

			GenTemplateGroup genTemplateGroup = new GenTemplateGroup();
			genTemplateGroup.setTemplateId(genTemplate.getId());
			genTemplateGroup.setGroupId(genGroup.getId());
			requireInserted(genTemplateGroupMapper.insert(genTemplateGroup), "模板组关系");
		}
		return true;
	}

	private boolean groupExistsCurrent(String groupName) {
		return genGroupMapper.selectCount(Wrappers.<GenGroup>lambdaQuery()
				.eq(GenGroup::getGroupName, groupName)
				.last("FOR UPDATE")) > 0;
	}

	private static TemplateUpdateResult result(boolean installed,
			TemplateUpdatePackageLoader.VerifiedManifest verified, String groupName) {
		return new TemplateUpdateResult(installed, verified.manifest().revision(), verified.digest(),
				groupName, verified.manifest().templates().size());
	}

	private static String installedGroupName(String groupName, String revision) {
		return groupName + "@" + revision;
	}

	private static void requireInserted(int inserted, String subject) {
		if (inserted != 1) throw new CheckedException(subject + "保存失败");
	}

}
