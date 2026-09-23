

package com.lotus.bixi.upms.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.core.exception.ErrorCodes;
import com.lotus.bixi.common.core.util.MsgUtils;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.log.annotation.SysLog;
import com.lotus.bixi.common.security.annotation.HasPermission;
import com.lotus.bixi.common.security.annotation.Inner;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.entity.SysUser;
import com.lotus.bixi.upms.api.service.UserQueryService;
import com.lotus.bixi.upms.api.vo.UserExcelVO;
import com.lotus.bixi.upms.api.vo.UserOptionVO;
import com.lotus.bixi.upms.api.vo.UserVO;
import com.lotus.bixi.upms.service.SysUserService;
import com.pig4cloud.plugin.excel.annotation.RequestExcel;
import com.pig4cloud.plugin.excel.annotation.ResponseExcel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@RestController
@AllArgsConstructor
@RequestMapping("/user")
@Tag(description = "user", name = "用户管理模块")
@SecurityRequirement(name = HttpHeaders.AUTHORIZATION)
public class SysUserController {

    private final SysUserService userService;

    private final UserQueryService userQueryService;

    /**
     * 获取指定用户全部信息
     *
     * @return 用户信息
     */
    @Inner
    @GetMapping(value = {"/info/query"})
    public R info(@RequestParam(required = false) String username, @RequestParam(required = false) String phone) {
        UserDTO query = new UserDTO();
        query.setUsername(username);
        query.setPhone(phone);
        return userQueryService.info(query);
    }

    /**
     * 获取当前用户全部信息
     *
     * @return 用户信息
     */
    @GetMapping(value = {"/info"})
    public R info() {
        String username = SecurityUtils.getUser().getUsername();
        SysUser user = userService.getOne(Wrappers.<SysUser>query().lambda().eq(SysUser::getUsername, username));
        if (user == null) {
            return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_USER_QUERY_ERROR));
        }
        return R.ok(userService.findUserInfo(user));
    }

    /**
     * 获取当前登录用户的个人资料，不接受客户端指定用户ID。
     *
     * @return 当前用户资料
     */
    @GetMapping("/me")
    public R<UserVO> currentUser() {
        return R.ok(userService.selectUserVoById(SecurityUtils.getUser().getId()));
    }

    /**
     * 通过ID查询用户信息
     *
     * @param id ID
     * @return 用户信息
     */
    @GetMapping("/details/{id}")
    @HasPermission("sys_user_view")
    public R user(@PathVariable Long id) {
        return R.ok(userService.selectUserVoById(id));
    }

    /**
     * 查询用户信息
     *
     * @param query 查询条件
     * @return 不为空返回用户名
     */
    @Inner(value = false)
    @GetMapping("/details")
    public R getDetails(@ParameterObject SysUser query) {
        SysUser sysUser = userService.getOne(Wrappers.query(query), false);
        return R.ok(sysUser == null ? null : CommonConstants.SUCCESS);
    }

    /**
     * 删除用户信息
     *
     * @param ids ID
     * @return R
     */
    @SysLog("删除用户信息")
    @DeleteMapping
    @HasPermission("sys_user_del")
    @Operation(summary = "删除用户", description = "根据ID删除用户")
    public R userDel(@RequestBody Long[] ids) {
        return R.ok(userService.deleteUserByIds(ids));
    }

    /**
     * 添加用户
     *
     * @param userDto 用户信息
     * @return success/false
     */
    @SysLog("添加用户")
    @PostMapping
    @HasPermission("sys_user_add")
    public R user(@RequestBody UserDTO userDto) {
        return R.ok(userService.saveUser(userDto));
    }

    /**
     * 更新用户信息
     *
     * @param userDto 用户信息
     * @return R
     */
    @SysLog("更新用户信息")
    @PutMapping
    @HasPermission("sys_user_edit")
    public R updateUser(@Valid @RequestBody UserDTO userDto) {
        return R.ok(userService.updateUser(userDto));
    }

    /**
     * 分页查询用户
     *
     * @param page    参数集
     * @param userDTO 查询参数列表
     * @return 用户集合
     */
    @GetMapping("/page")
    @HasPermission("sys_user_view")
    public R getUserPage(@ParameterObject Page page, @ParameterObject UserDTO userDTO) {
        return R.ok(userService.getUsersWithRolePage(page, userDTO));
    }

    @GetMapping("/options")
    @HasPermission({"sys_user_view", "sys_notice_add", "sys_notice_edit"})
    public R<List<UserOptionVO>> userOptions() {
        Page<SysUser> page = userService.page(new Page<>(1, 1000, false), Wrappers.<SysUser>lambdaQuery()
                .select(SysUser::getId, SysUser::getUsername, SysUser::getName)
                .orderByAsc(SysUser::getId));
        return R.ok(page.getRecords().stream()
                .map(user -> new UserOptionVO(user.getId(), user.getUsername(), user.getName())).toList());
    }

    /**
     * 修改个人信息
     *
     * @param userDto userDto
     * @return success/false
     */
    @SysLog("修改个人信息")
    @PutMapping("/edit")
    public R updateUserInfo(@Valid @RequestBody UserDTO userDto) {
        return R.ok(userService.updateUserInfo(userDto));
    }

    /**
     * 导出excel 表格
     *
     * @param userDTO 查询条件
     * @return
     */
    @ResponseExcel
    @GetMapping("/export")
    @HasPermission("sys_user_export")
    public List export(UserDTO userDTO) {
        return userService.listUser(userDTO);
    }

    /**
     * 导入用户
     *
     * @param excelVOList   用户列表
     * @param bindingResult 错误信息列表
     * @return R
     */
    @SysLog("导入用户")
    @PostMapping("/import")
    @HasPermission("sys_user_import")
    public R importUser(@RequestExcel List<UserExcelVO> excelVOList, BindingResult bindingResult) {
        return userService.importUser(excelVOList, bindingResult);
    }

    /**
     * 锁定指定用户
     *
     * @param username 用户名
     * @return R
     */@Inner
    @PutMapping("/lock/{username}")
    public R lockUser(@PathVariable String username) {
        return R.ok(userService.lockUser(username));
    }

    /**
     * 解锁用户
     *
     * @param username 用户名
     * @return R
     */
    @Inner
    @PutMapping("/unlock/{username}")
    public R unlockUser(@PathVariable String username) {
        return R.ok(userService.unlockUser(username));
    }

    @PutMapping("/password")
    public R password(@RequestBody UserDTO userDto) {
        String username = SecurityUtils.getUser().getUsername();
        userDto.setUsername(username);
        return userService.changePassword(userDto);
    }

    @PostMapping("/check")
    public R check(String password) {
        return userService.checkPassword(password);
    }

}
