package com.lotus.bixi.upms.service;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Resolves the authenticated operator for UPMS recovery actions. */
@Service
@ConditionalOnWorkflowEnabled
public class UpmsRecoveryAccessService {

    public BixiUser currentUser() {
        BixiUser user = SecurityUtils.getUser();
        if (user == null || user.getId() == null) {
            throw new AccessDeniedException("请先登录");
        }
        return user;
    }
}
