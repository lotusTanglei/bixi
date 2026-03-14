package com.lotus.bixi.common.security.component;

import cn.hutool.core.util.StrUtil;
import com.lotus.bixi.common.security.constant.FormPermissionConstant;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 表单权限校验服务
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Slf4j
@Service("formPms")
@RequiredArgsConstructor
public class FormPermissionService implements FormPermissionConstant {

    /**
     * 校验表单权限
     *
     * @param formKey  表单标识
     * @param permType 权限类型
     * @return {boolean}
     */
    public boolean hasFormPermission(String formKey, String permType) {
        if (StrUtil.isEmpty(formKey)) {
            return false;
        }
        if (StrUtil.isEmpty(permType)) {
            permType = PERM_TYPE_VIEW;
        }

        BixiUser user = SecurityUtils.getUser();
        if (user == null) {
            return false;
        }

        Set<String> permissions = getUserFormPermissions(user, formKey);
        if (permissions.isEmpty()) {
            return false;
        }

        return checkPermission(permissions, permType);
    }

    /**
     * 获取用户对指定表单的权限集合
     *
     * @param user    用户
     * @param formKey 表单标识
     * @return 权限集合
     */
    private Set<String> getUserFormPermissions(BixiUser user, String formKey) {
        Set<String> permissions = new HashSet<>();
        Collection<? extends GrantedAuthority> authorities = user.getAuthorities();

        String formPermPrefix = "form:" + formKey + ":";

        for (GrantedAuthority authority : authorities) {
            String auth = authority.getAuthority();
            if (StrUtil.startWith(auth, formPermPrefix)) {
                permissions.add(StrUtil.removePrefix(auth, formPermPrefix));
            }
        }

        return permissions;
    }

    /**
     * 校验权限类型
     *
     * @param permissions 用户拥有的权限
     * @param permType    需要的权限类型
     * @return 是否有权限
     */
    private boolean checkPermission(Set<String> permissions, String permType) {
        if (permissions.contains(PERM_TYPE_HIDDEN)) {
            return false;
        }

        if (permissions.contains(PERM_TYPE_EDIT)) {
            return true;
        }

        if (PERM_TYPE_VIEW.equals(permType) || PERM_TYPE_READONLY.equals(permType)) {
            return permissions.contains(PERM_TYPE_VIEW) || permissions.contains(PERM_TYPE_READONLY);
        }

        if (PERM_TYPE_EDIT.equals(permType)) {
            return permissions.contains(PERM_TYPE_EDIT);
        }

        return permissions.contains(permType);
    }

}
