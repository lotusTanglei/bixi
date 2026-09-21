package com.lotus.bixi.workflow.api.event;

import java.time.Instant;

/** Provenance captured by UPMS, not a substitute for authenticating the source service. */
public record WorkflowActorSnapshot(long userId, String username, String tenantScope,
                                    String originatingService, Instant authorizedAt) {
    public WorkflowActorSnapshot {
        if (userId <= 0) throw new IllegalArgumentException("userId无效");
        WorkflowEventValidation.text(username, "username", 128);
        if (!"default".equals(tenantScope) || !"upms".equals(originatingService)) {
            throw new IllegalArgumentException("actor来源无效");
        }
        WorkflowEventValidation.instant(authorizedAt, "authorizedAt");
    }
}
