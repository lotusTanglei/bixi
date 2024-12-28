
package com.lotus.bixi.generator.controller;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import cn.smallbun.screw.boot.config.Screw;
import cn.smallbun.screw.boot.properties.ScrewProperties;
import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.generator.entity.GenDatasourceConfig;
import com.lotus.bixi.generator.service.GenDatasourceConfigService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.core.util.SpringContextHolder;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.common.xss.core.XssCleanIgnore;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;

/**
 * 数据源管理
 *
 * @author tanglei
 * @date 2019-03-31 16:00:20
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/dsconfig")
public class GenDsConfigController {

	private final GenDatasourceConfigService datasourceConfigService;

	private final Screw screw;

	/**
	 * 分页查询
	 * @param page 分页对象
	 * @param datasourceConf 数据源表
	 * @return
	 */
	@GetMapping("/page")
	public R getSysDatasourceConfPage(Page page, GenDatasourceConfig datasourceConf) {
		return R.ok(datasourceConfigService.page(page,
				Wrappers.<GenDatasourceConfig>lambdaQuery()
					.like(StrUtil.isNotBlank(datasourceConf.getDsName()), GenDatasourceConfig::getDsName,
							datasourceConf.getDsName())));
	}

	/**
	 * 查询全部数据源
	 * @return
	 */
	@GetMapping("/list")
	@Inner(value = false)
	public R list() {
		return R.ok(datasourceConfigService.list());
	}

	/**
	 * 通过id查询数据源表
	 * @param id id
	 * @return R
	 */
	@GetMapping("/{id}")
	public R getById(@PathVariable("id") Long id) {
		return R.ok(datasourceConfigService.getById(id));
	}

	/**
	 * 新增数据源表
	 * @param datasourceConf 数据源表
	 * @return R
	 */
	@PostMapping
	@XssCleanIgnore
	public R save(@RequestBody GenDatasourceConfig datasourceConf) {
		return R.ok(datasourceConfigService.saveDsByEnc(datasourceConf));
	}

	/**
	 * 修改数据源表
	 * @param conf 数据源表
	 * @return R
	 */
	@PutMapping
	@XssCleanIgnore
	public R updateById(@RequestBody GenDatasourceConfig conf) {
		return R.ok(datasourceConfigService.updateDsByEnc(conf));
	}

	/**
	 * 通过id删除数据源表
	 * @param ids id
	 * @return R
	 */
	@DeleteMapping
	public R removeById(@RequestBody Long[] ids) {
		return R.ok(datasourceConfigService.removeByDsId(ids));
	}

	/**
	 * 查询数据源对应的文档
	 * @param dsName 数据源名称
	 */
	@SneakyThrows
	@GetMapping("/doc")
	public void generatorDoc(String dsName, HttpServletResponse response) {
		// 设置指定的数据源
		DynamicRoutingDataSource dynamicRoutingDataSource = SpringContextHolder.getBean(DynamicRoutingDataSource.class);
		DynamicDataSourceContextHolder.push(dsName);
		DataSource dataSource = dynamicRoutingDataSource.determineDataSource();

		// 设置指定的目标表
		ScrewProperties screwProperties = SpringContextHolder.getBean(ScrewProperties.class);

		// 生成
		byte[] data = screw.documentGeneration(dsName, dataSource, screwProperties).toByteArray();
		response.reset();
		response.addHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length));
		response.setContentType("application/octet-stream");
		IoUtil.write(response.getOutputStream(), Boolean.FALSE, data);
	}

}
