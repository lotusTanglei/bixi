package com.lotus.bixi.upms.api.service;

import com.lotus.bixi.upms.api.dto.CandidateRole;

/** Transport-neutral lookup of server-owned role state. */
public interface CandidateRoleQueryService {
    CandidateRole findById(Long roleId);
}
