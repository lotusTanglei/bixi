

package com.lotus.bixi.upms.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lotus.bixi.upms.api.dto.UserDTO;
import com.lotus.bixi.upms.api.dto.UserInfo;
import com.lotus.bixi.upms.api.entity.*;
import com.lotus.bixi.upms.api.util.ParamResolver;
import com.lotus.bixi.upms.api.vo.UserExcelVO;
import com.lotus.bixi.upms.api.vo.UserVO;
import com.lotus.bixi.upms.mapper.SysUserMapper;
import com.lotus.bixi.upms.mapper.SysUserPostMapper;
import com.lotus.bixi.upms.mapper.SysUserRoleMapper;
import com.lotus.bixi.upms.mapper.SysTenantMapper;
import com.lotus.bixi.upms.service.*;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.common.core.constant.CommonConstants;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.core.exception.ErrorCodes;
import com.lotus.bixi.common.core.util.MsgUtils;
import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.common.security.util.PasswordPolicyValidator;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.pig4cloud.plugin.excel.vo.ErrorMessage;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Service
@AllArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private static final PasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final SysMenuService sysMenuService;

    private final SysRoleService sysRoleService;

    private final SysPostService sysPostService;

    private final SysDeptService sysDeptService;

    private final SysUserRoleMapper sysUserRoleMapper;

    private final SysUserPostMapper sysUserPostMapper;

    private final SysTenantMapper sysTenantMapper;

    private final CacheManager cacheManager;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 保存用户信息
     *
     * @param userDto DTO 对象
     * @return success/fail
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean saveUser(UserDTO userDto) {
        if (StrUtil.isBlank(userDto.getUsername())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        Long tenantId = TenantContextHolder.get();
        if (tenantId == null) {
            throw new IllegalStateException("tenant_not_set");
        }
        var tenant = sysTenantMapper.selectById(tenantId);
        if (tenant == null || "1".equals(tenant.getStatus())) {
            throw new IllegalStateException("tenant_disabled");
        }
        if (tenant.getMaxUserCount() != null && tenant.getMaxUserCount() >= 0
                && baseMapper.selectCount(Wrappers.<SysUser>lambdaQuery()) >= tenant.getMaxUserCount()) {
            throw new IllegalStateException("tenant_user_limit");
        }
        if (!PasswordPolicyValidator.isStrong(userDto.getPassword())) {
            throw new RuntimeException(MsgUtils.getMessage(ErrorCodes.SYS_PASSWORD_WEAK));
        }
        requireOwnedRelations(userDto);
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(userDto, sysUser);
        sysUser.setDelFlag(CommonConstants.STATUS_NORMAL);
        sysUser.setPassword(ENCODER.encode(userDto.getPassword()));
        baseMapper.insert(sysUser);
        // 保存用户岗位信息
        Optional.ofNullable(userDto.getPost()).ifPresent(posts -> {
            posts.stream().map(postId -> {
                SysUserPost userPost = new SysUserPost();
                userPost.setUserId(sysUser.getId());
                userPost.setPostId(postId);
                return userPost;
            }).forEach(sysUserPostMapper::insert);
        });

        // 如果角色为空，赋默认角色
        if (CollUtil.isEmpty(userDto.getRole())) {
            // 获取默认角色编码
            String defaultRole = ParamResolver.getStr("USER_DEFAULT_ROLE");
            // 默认角色
            SysRole sysRole = sysRoleService
                    .getOne(Wrappers.<SysRole>lambdaQuery().eq(SysRole::getCode, defaultRole));
            userDto.setRole(Collections.singletonList(sysRole.getId()));
        }

        // 插入用户角色关系表
        userDto.getRole().stream().map(roleId -> {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(sysUser.getId());
            userRole.setRoleId(roleId);
            return userRole;
        }).forEach(sysUserRoleMapper::insert);
        return Boolean.TRUE;
    }

    /**
     * 通过查用户的全部信息
     *
     * @param sysUser 用户
     * @return
     */
    @Override
    public UserInfo findUserInfo(SysUser sysUser) {
        UserInfo userInfo = new UserInfo();
        userInfo.setSysUser(sysUser);
        // 设置角色列表 （ID）
        List<Long> roleIds = sysRoleService.findRolesByUserId(sysUser.getId())
                .stream()
                .map(SysRole::getId)
                .collect(Collectors.toList());
        userInfo.setRoles(ArrayUtil.toArray(roleIds, Long.class));
		userInfo.setDataScope(roleIds.stream().map(sysRoleService::getById)
				.filter(Objects::nonNull).map(SysRole::getDataScope).filter(Objects::nonNull)
				.min(Comparator.naturalOrder()).orElse("4"));

        // 设置权限列表（menu.permission）
        Set<String> permissions = new HashSet<>();
        roleIds.forEach(roleId -> {
            List<String> permissionList = sysMenuService.findMenuByRoleId(roleId)
                    .stream()
                    .filter(menu -> StrUtil.isNotEmpty(menu.getPermission()))
                    .map(SysMenu::getPermission)
                    .collect(Collectors.toList());
            permissions.addAll(permissionList);
        });
        userInfo.setPermissions(ArrayUtil.toArray(permissions, String.class));
        return userInfo;
    }

    /**
     * 分页查询用户信息（含有角色信息）
     *
     * @param page    分页对象
     * @param userDTO 参数列表
     * @return
     */
    @Override
    public IPage getUsersWithRolePage(Page page, UserDTO userDTO) {
        return baseMapper.getUserVosPage(page, userDTO);
    }

    /**
     * 通过ID查询用户信息
     *
     * @param id 用户ID
     * @return 用户信息
     */
    @Override
    public UserVO selectUserVoById(Long id) {
        return baseMapper.getUserVoById(id);
    }

    /**
     * 删除用户
     *
     * @param ids 用户ID 列表
     * @return Boolean
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteUserByIds(Long[] ids) {
        if (ids == null || ids.length == 0 || Arrays.asList(ids).contains(null)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        List<Long> requested = Arrays.stream(ids).distinct().toList();
        List<SysUser> userList = baseMapper.selectByIds(requested);
        if (userList.size() != requested.size()) {
            throw new IllegalArgumentException("用户不存在或无权访问");
        }
        for (Long id : requested) {
            if (baseMapper.deleteById(id) != 1) {
                throw new IllegalArgumentException("用户不存在或无权访问");
            }
        }
        sysUserRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().in(SysUserRole::getUserId, requested));
        evictUserDetailsAfterCommit(userList.stream().map(SysUser::getUsername).toList());
        return Boolean.TRUE;
    }

    private void evictUserDetailsAfterCommit(Collection<String> usernames) {
        String prefix = CacheConstants.tenantKey(CacheConstants.USER_DETAILS, TenantContextHolder.get());
        Runnable evict = () -> {
            Cache cache = cacheManager.getCache(CacheConstants.USER_DETAILS);
            usernames.forEach(username -> cache.evictIfPresent(prefix + username));
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }

    @Override
    @CacheEvict(value = CacheConstants.USER_DETAILS,
            key = "T(com.lotus.bixi.common.core.constant.CacheConstants).currentTenantKey('user_details') + #userDto.username")
    public Boolean updateUserInfo(UserDTO userDto) {
        SysUser sysUser = new SysUser();
        sysUser.setPhone(userDto.getPhone());
        sysUser.setId(SecurityUtils.getUser().getId());
        sysUser.setAvatar(userDto.getAvatar());
        sysUser.setNickname(userDto.getNickname());
        sysUser.setName(userDto.getName());
        sysUser.setEmail(userDto.getEmail());
        return this.updateById(sysUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateUser(UserDTO userDto) {
        if (StrUtil.isBlank(userDto.getUsername())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        SysUser current = this.getById(userDto.getId());
        if (current == null) {
            throw new IllegalArgumentException("用户不存在或无权访问");
        }
        if (!Objects.equals(current.getUsername(), userDto.getUsername())) {
            throw new IllegalArgumentException("用户名不能修改");
        }
        requireOwnedRelations(userDto);
        // 更新用户表信息
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(userDto, sysUser);
        sysUser.setUpdateTime(LocalDateTime.now());
        if (StrUtil.isNotBlank(userDto.getPassword())) {
            if (!PasswordPolicyValidator.isStrong(userDto.getPassword())) {
                throw new RuntimeException(MsgUtils.getMessage(ErrorCodes.SYS_PASSWORD_WEAK));
            }
            sysUser.setPassword(ENCODER.encode(userDto.getPassword()));
        }
        if (!this.updateById(sysUser)) {
            throw new IllegalArgumentException("用户不存在或无权访问");
        }

        // 更新用户角色表
        if (Objects.nonNull(userDto.getRole())) {
            // 删除用户角色关系
            sysUserRoleMapper
                    .delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, userDto.getId()));
            userDto.getRole().stream().map(roleId -> {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(sysUser.getId());
                userRole.setRoleId(roleId);
                return userRole;
            }).forEach(SysUserRole::insert);
        }

        if (Objects.nonNull(userDto.getPost())) {
            // 删除用户岗位关系
            sysUserPostMapper
                    .delete(Wrappers.<SysUserPost>lambdaQuery().eq(SysUserPost::getUserId, userDto.getId()));
            userDto.getPost().stream().map(postId -> {
                SysUserPost userPost = new SysUserPost();
                userPost.setUserId(sysUser.getId());
                userPost.setPostId(postId);
                return userPost;
            }).forEach(SysUserPost::insert);
        }
        evictUserDetailsAfterCommit(List.of(userDto.getUsername()));
        return Boolean.TRUE;
    }

    private void requireOwnedRelations(UserDTO userDto) {
        if (CollUtil.isNotEmpty(userDto.getRole())) {
            List<Long> roleIds = userDto.getRole().stream().distinct().toList();
            if (roleIds.contains(null) || sysRoleService.count(Wrappers.<SysRole>lambdaQuery()
                    .in(SysRole::getId, roleIds)) != roleIds.size()) {
                throw new IllegalArgumentException("角色不存在或不属于当前租户");
            }
        }
        if (CollUtil.isNotEmpty(userDto.getPost())) {
            List<Long> postIds = userDto.getPost().stream().distinct().toList();
            if (postIds.contains(null) || sysPostService.count(Wrappers.<SysPost>lambdaQuery()
                    .in(SysPost::getId, postIds)) != postIds.size()) {
                throw new IllegalArgumentException("岗位不存在或不属于当前租户");
            }
        }
    }

    /**
     * 查询全部的用户
     *
     * @param userDTO 查询条件
     * @return list
     */
    @Override
    public List<UserExcelVO> listUser(UserDTO userDTO) {
        // 根据数据权限查询全部的用户信息
        List<UserVO> voList = baseMapper.selectVoList(userDTO);
        // 转换成execl 对象输出
        return voList.stream().map(userVO -> {
            UserExcelVO excelVO = new UserExcelVO();
            BeanUtils.copyProperties(userVO, excelVO);
            String roleNameList = userVO.getRoleList()
                    .stream()
                    .map(SysRole::getName)
                    .collect(Collectors.joining(StrUtil.COMMA));
            excelVO.setRoleNameList(roleNameList);
            String postNameList = userVO.getPostList()
                    .stream()
                    .map(SysPost::getName)
                    .collect(Collectors.joining(StrUtil.COMMA));
            excelVO.setPostNameList(postNameList);
            return excelVO;
        }).collect(Collectors.toList());
    }

    /**
     * excel 导入用户, 插入正确的 错误的提示行号
     *
     * @param excelVOList   excel 列表数据
     * @param bindingResult 错误数据
     * @return ok fail
     */
    @Override
    public R importUser(List<UserExcelVO> excelVOList, BindingResult bindingResult) {
        // 通用校验获取失败的数据
        List<ErrorMessage> errorMessageList = (List<ErrorMessage>) bindingResult.getTarget();
        List<SysDept> deptList = sysDeptService.list();
        List<SysRole> roleList = sysRoleService.list();
        List<SysPost> postList = sysPostService.list();

        // 执行数据插入操作 组装 UserDto
        for (UserExcelVO excel : excelVOList) {
            // 个性化校验逻辑
            List<SysUser> userList = this.list();

            Set<String> errorMsg = new HashSet<>();
            // 校验用户名是否存在
            boolean exsitUserName = userList.stream()
                    .anyMatch(sysUser -> excel.getUsername().equals(sysUser.getUsername()));

            if (exsitUserName) {
                errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_USER_USERNAME_EXISTING, excel.getUsername()));
            }

            // 判断输入的部门名称列表是否合法
            Optional<SysDept> deptOptional = deptList.stream()
                    .filter(dept -> excel.getDeptName().equals(dept.getName()))
                    .findFirst();
            if (!deptOptional.isPresent()) {
                errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_DEPT_DEPTNAME_INEXISTENCE, excel.getDeptName()));
            }

            // 判断输入的角色名称列表是否合法
            List<String> roleNameList = StrUtil.split(excel.getRoleNameList(), StrUtil.COMMA);
            List<SysRole> roleCollList = roleList.stream()
                    .filter(role -> roleNameList.stream().anyMatch(name -> role.getName().equals(name)))
                    .collect(Collectors.toList());

            if (roleCollList.size() != roleNameList.size()) {
                errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_ROLE_ROLENAME_INEXISTENCE, excel.getRoleNameList()));
            }

            // 判断输入的部门名称列表是否合法
            List<String> postNameList = StrUtil.split(excel.getPostNameList(), StrUtil.COMMA);
            List<SysPost> postCollList = postList.stream()
                    .filter(post -> postNameList.stream().anyMatch(name -> post.getName().equals(name)))
                    .collect(Collectors.toList());

            if (postCollList.size() != postNameList.size()) {
                errorMsg.add(MsgUtils.getMessage(ErrorCodes.SYS_POST_POSTNAME_INEXISTENCE, excel.getPostNameList()));
            }

            // 数据合法情况
            if (CollUtil.isEmpty(errorMsg)) {
                insertExcelUser(excel, deptOptional, roleCollList, postCollList);
            } else {
                // 数据不合法情况
                errorMessageList.add(new ErrorMessage(excel.getLineNum(), errorMsg));
            }

        }

        if (CollUtil.isNotEmpty(errorMessageList)) {
            return R.failed(errorMessageList);
        }
        return R.ok();
    }

    /**
     * 插入excel User
     */
    private void insertExcelUser(UserExcelVO excel, Optional<SysDept> deptOptional, List<SysRole> roleCollList,
                                 List<SysPost> postCollList) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUsername(excel.getUsername());
        userDTO.setPhone(excel.getPhone());
        userDTO.setNickname(excel.getNickname());
        userDTO.setName(excel.getName());
        userDTO.setEmail(excel.getEmail());
        // 批量导入初始密码为手机号
        userDTO.setPassword(userDTO.getPhone());
        // 根据部门名称查询部门ID
        userDTO.setDeptId(deptOptional.get().getId());
        // 插入岗位名称
        List<Long> postIdList = postCollList.stream().map(SysPost::getId).collect(Collectors.toList());
        userDTO.setPost(postIdList);
        // 根据角色名称查询角色ID
        List<Long> roleIdList = roleCollList.stream().map(SysRole::getId).collect(Collectors.toList());
        userDTO.setRole(roleIdList);
        // 插入用户
        this.saveUser(userDTO);
    }

    /**
     * 注册用户 赋予用户默认角色
     *
     * @param userDto 用户信息
     * @return success/false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean registerUser(UserDTO userDto) {
        // 判断用户名是否存在
        SysUser sysUser = this.getOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, userDto.getUsername()));
        if (sysUser != null) {
            String message = MsgUtils.getMessage(ErrorCodes.SYS_USER_USERNAME_EXISTING, userDto.getUsername());
            throw new RuntimeException(message);
        }
        return saveUser(userDto);
    }

    /**
     * 锁定用户
     *
     * @param username 用户名
     * @return
     */
    @Override
    @CacheEvict(value = CacheConstants.USER_DETAILS,
            key = "T(com.lotus.bixi.common.core.constant.CacheConstants).currentTenantKey('user_details') + #username")
    public Boolean lockUser(String username) {
        SysUser sysUser = baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));

        if (Objects.nonNull(sysUser)) {
            sysUser.setLockFlag(CommonConstants.STATUS_LOCK);
            return baseMapper.updateById(sysUser) > 0;
        }
        return false;
    }

    @Override
    @CacheEvict(value = CacheConstants.USER_DETAILS,
            key = "T(com.lotus.bixi.common.core.constant.CacheConstants).currentTenantKey('user_details') + #username")
    public Boolean unlockUser(String username) {
        SysUser sysUser = baseMapper.selectOne(Wrappers.<SysUser>lambdaQuery().eq(SysUser::getUsername, username));

        if (Objects.nonNull(sysUser)) {
            sysUser.setLockFlag(CommonConstants.STATUS_NORMAL);
            redisTemplate.delete(CacheConstants.tenantKey(CacheConstants.LOGIN_FAIL_KEY, TenantContextHolder.get()) + username);
            return baseMapper.updateById(sysUser) > 0;
        }
        return false;
    }

    @Override
    @CacheEvict(value = CacheConstants.USER_DETAILS,
            key = "T(com.lotus.bixi.common.core.constant.CacheConstants).currentTenantKey('user_details') + #userDto.username")
    public R changePassword(UserDTO userDto) {
        SysUser sysUser = baseMapper.selectById(SecurityUtils.getUser().getId());
        if (Objects.isNull(sysUser)) {
            return R.failed("用户不存在");
        }

        if (StrUtil.isEmpty(userDto.getPassword())) {
            return R.failed("原密码不能为空");
        }

        if (!ENCODER.matches(userDto.getPassword(), sysUser.getPassword())) {
            log.info("原密码错误，修改个人信息失败:{}", userDto.getUsername());
            return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_USER_UPDATE_PASSWORDERROR));
        }

        if (StrUtil.isEmpty(userDto.getNewpassword())) {
            return R.failed("新密码不能为空");
        }
        if (!PasswordPolicyValidator.isStrong(userDto.getNewpassword())) {
            return R.failed(MsgUtils.getMessage(ErrorCodes.SYS_PASSWORD_WEAK));
        }
        String password = ENCODER.encode(userDto.getNewpassword());

        this.update(Wrappers.<SysUser>lambdaUpdate()
                .set(SysUser::getPassword, password)
                .eq(SysUser::getId, sysUser.getId()));
        return R.ok();
    }

    @Override
    public R checkPassword(String password) {
        SysUser sysUser = baseMapper.selectById(SecurityUtils.getUser().getId());

        if (!ENCODER.matches(password, sysUser.getPassword())) {
            log.info("原密码错误");
            return R.failed("密码输入错误");
        } else {
            return R.ok();
        }
    }

}
