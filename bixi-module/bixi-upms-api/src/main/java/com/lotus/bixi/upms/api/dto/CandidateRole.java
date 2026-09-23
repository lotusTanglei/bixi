package com.lotus.bixi.upms.api.dto;

import java.io.Serializable;

/** Server-owned role state used for workflow candidate resolution. */
public record CandidateRole(Long roleId, boolean active, Long tenantId) implements Serializable {
    private static final long serialVersionUID = 1L;
}
