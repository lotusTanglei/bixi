

package com.lotus.bixi.upms.controller;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.upms.api.entity.SysOauthClientDetails;
import com.lotus.bixi.upms.service.SysOauthClientDetailsService;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.annotation.Inner;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 唐磊
 * @since 2018-05-15
 */
@RestController
@AllArgsConstructor
@RequestMapping("/client")
@Tag(description = "client", name = "客户端管理模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysClientController {

    private final SysOauthClientDetailsService clientDetailsService;

    /**
     * 通过ID查询
     *
     * @param clientId clientId
     * @return SysOauthClientDetails
     */
    @GetMapping("/{clientId}")
    public R getByClientId(@PathVariable String clientId) {
        SysOauthClientDetails details = clientDetailsService
                .getOne(Wrappers.<SysOauthClientDetails>lambdaQuery().eq(SysOauthClientDetails::getClientId, clientId));
        return R.ok(details);
    }

    /**
     * 简单分页查询
     *
     * @param page                  分页对象
     * @param sysOauthClientDetails 系统终端
     * @return
     */
    @GetMapping("/page")
    public R getOauthClientDetailsPage(@ParameterObject Page page,
                                       @ParameterObject SysOauthClientDetails sysOauthClientDetails) {
        LambdaQueryWrapper<SysOauthClientDetails> wrapper = Wrappers.<SysOauthClientDetails>lambdaQuery()
                .like(StrUtil.isNotBlank(sysOauthClientDetails.getClientId()), SysOauthClientDetails::getClientId,
                        sysOauthClientDetails.getClientId())
                .like(StrUtil.isNotBlank(sysOauthClientDetails.getClientSecret()), SysOauthClientDetails::getClientSecret,
                        sysOauthClientDetails.getClientSecret());
        return R.ok(clientDetailsService.page(page, wrapper));
    }

    /**
     * 添加
     *
     * @param clientDetails 实体
     * @return success/false
     */
    @SysLog("添加终端")
    @PostMapping
    @HasPermission("sys_client_add")
    public R add(@Valid @RequestBody SysOauthClientDetails clientDetails) {
        return R.ok(clientDetailsService.saveClient(clientDetails));
    }

    /**
     * 删除
     *
     * @param ids ID 列表
     * @return success/false
     */
    @SysLog("删除终端")
    @DeleteMapping
    @HasPermission("sys_client_del")
    public R removeById(@RequestBody Long[] ids) {
        clientDetailsService.removeBatchByIds(CollUtil.toList(ids));
        return R.ok();
    }

    /**
     * 编辑
     *
     * @param clientDetails 实体
     * @return success/false
     */
    @SysLog("编辑终端")
    @PutMapping
    @HasPermission("sys_client_edit")
    public R update(@Valid @RequestBody SysOauthClientDetails clientDetails) {
        return R.ok(clientDetailsService.updateClientById(clientDetails));
    }

    @Inner
    @GetMapping("/getClientDetailsById/{clientId}")
    public R getClientDetailsById(@PathVariable String clientId) {
        return R.ok(clientDetailsService.getOne(
                Wrappers.<SysOauthClientDetails>lambdaQuery().eq(SysOauthClientDetails::getClientId, clientId), false));
    }

    /**
     * 同步缓存字典
     *
     * @return R
     */
    @SysLog("同步终端")
    @PutMapping("/sync")
    public R sync() {
        return clientDetailsService.syncClientCache();
    }

    /**
     * 导出所有客户端
     *
     * @return excel
     */
    @ResponseExcel
    @SysLog("导出excel")
    @GetMapping("/export")
    public List<SysOauthClientDetails> export(SysOauthClientDetails sysOauthClientDetails) {
        return clientDetailsService.list(Wrappers.query(sysOauthClientDetails));
    }

}
