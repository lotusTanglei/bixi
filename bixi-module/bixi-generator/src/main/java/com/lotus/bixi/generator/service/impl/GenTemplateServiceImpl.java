package com.lotus.bixi.generator.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.smallbun.screw.core.constant.DefaultConstants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.common.core.exception.CheckedException;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.entity.GenGroup;
import com.lotus.bixi.generator.entity.GenTemplate;
import com.lotus.bixi.generator.entity.GenTemplateGroup;
import com.lotus.bixi.generator.mapper.GenGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateGroupMapper;
import com.lotus.bixi.generator.mapper.GenTemplateMapper;
import com.lotus.bixi.generator.service.GenTemplateService;
import com.lotus.bixi.generator.util.vo.GenTemplateFileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模板
 *
 * @author 唐磊
 * @date 2023-02-21 11:08:43
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenTemplateServiceImpl extends ServiceImpl<GenTemplateMapper, GenTemplate>
		implements GenTemplateService {

	private final GenTemplateGroupMapper genTemplateGroupMapper;

	private final GenGroupMapper genGroupMapper;

	private final BixiGeneratorDefaultProperties defaultProperties;

	/**
	 * 在线更新
	 * @return {@link R }
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public R onlineUpdate() {
		// 获取 config.json 和 version 文件
		Map<String, Object> configAndVersion = getConfigAndVersion();
		JSONObject configJsonObj = (JSONObject) configAndVersion.get("configJsonObj");
		String versionFile = (String) configAndVersion.get("versionFile");

		// 查询出全部的模板组名称
		Set<String> cgtmConfigGroupNames = configJsonObj.keySet();

		String cgtmConfigGroupName = cgtmConfigGroupNames.iterator().next();
		// 根据模板组名称+version 查询是否存在，不存在则新增，存在跳过
		boolean exists = genGroupMapper.exists(Wrappers.<GenGroup>lambdaQuery()
			.eq(GenGroup::getGroupName, cgtmConfigGroupName + versionFile));

		if (exists) {
			return R.failed("已是最新版本，无需更新！");
		}

		// 插入新的模板组（名称 + VERSION）, 再解析 config.json group 里面的所有模板
		insertTemplateFiles(versionFile, configJsonObj, cgtmConfigGroupName);
		return R.ok("更新成功，版本号:" + versionFile);
	}

	/**
	 * 检查版本
	 * @return {@link R }
	 */
	public R checkVersion() {
		// 关闭在线更新提示
		if (!defaultProperties.isAutoCheckVersion()) {
			return R.ok(true);
		}

		// 获取 config.json 和 version 文件
		Map<String, Object> configAndVersion = getConfigAndVersion();
		JSONObject configJsonObj = (JSONObject) configAndVersion.get("configJsonObj");
		String versionFile = (String) configAndVersion.get("versionFile");

		// 查询出全部的模板组名称
		Set<String> cgtmConfigGroupNames = configJsonObj.keySet();

		String cgtmConfigGroupName = cgtmConfigGroupNames.iterator().next();
		// 根据模板组名称+version 查询是否存在，不存在则新增，存在跳过
		boolean exists = genGroupMapper.exists(Wrappers.<GenGroup>lambdaQuery()
			.eq(GenGroup::getGroupName, cgtmConfigGroupName + versionFile));

		return R.ok(exists);
	}

	/**
	 * 获取配置和版本
	 * @return {@link Map }<{@link String }, {@link Object }>
	 */
	private Map<String, Object> getConfigAndVersion() {
		// 获取 config.json 和 version 文件
		String configFile = getCGTMFile("config.json");
		String versionFile = getCGTMFile("VERSION");

		// 解析 config.json
		JSONObject configJsonObj = JSONUtil.parseObj(configFile);

		// 将 configJsonObj 和 versionFile 放入 Map 中
		Map<String, Object> configAndVersion = new HashMap<>();
		configAndVersion.put("configJsonObj", configJsonObj);
		configAndVersion.put("versionFile", versionFile);

		return configAndVersion;
	}

	/**
	 * 插入模板文件
	 * @param version 版本
	 * @param configJsonObj config.json
	 * @param groupName 组名称
	 */
	private void insertTemplateFiles(String version, JSONObject configJsonObj, String groupName) {
		// 创建新的 group
		GenGroup genGroup = new GenGroup();
		genGroup.setGroupName(groupName + version);
		genGroupMapper.insert(genGroup);

		// 解析json配置文件
		List<GenTemplateFileVO> templateFileVOList = configJsonObj.getBeanList(groupName, GenTemplateFileVO.class);
		for (GenTemplateFileVO genTemplateFileVO : templateFileVOList) {
			// 1. 获取模板文件
			String templateFile = getCGTMFile(genTemplateFileVO.getTemplateFile());

			// 2. 插入模板文件
			GenTemplate genTemplate = new GenTemplate();
			genTemplate.setTemplateName(genTemplateFileVO.getTemplateName() + version);
			genTemplate.setTemplateDesc(genTemplateFileVO.getTemplateName() + version);
			genTemplate.setTemplateCode(templateFile);
			genTemplate.setGeneratorPath(genTemplateFileVO.getGeneratorPath());
			baseMapper.insert(genTemplate);

			// 3. 插入模板组关联
			GenTemplateGroup genTemplateGroup = new GenTemplateGroup();
			genTemplateGroup.setTemplateId(genTemplate.getId());
			genTemplateGroup.setGroupId(genGroup.getId());
			genTemplateGroupMapper.insert(genTemplateGroup);
		}
	}

	/**
	 * 获取 cgtmfile
	 * @param fileName 文件名
	 * @return {@link String }
	 */
	private String getCGTMFile(String fileName) {
		HttpResponse response = HttpRequest
			.get(String.format("%s/CGTM/raw/master/%s", DefaultConstants.CGTM_URL, fileName))
			.execute();

		if (response.getStatus() == HttpStatus.HTTP_OK || StrUtil.isNotBlank(response.body())) {
			return response.body();
		}
		else {
			log.warn("在线更新模板失败:{} ，Http Code:{}", fileName, response.getStatus());
			throw new CheckedException("在线更新模板失败，任务终止！");
		}
	}

}
