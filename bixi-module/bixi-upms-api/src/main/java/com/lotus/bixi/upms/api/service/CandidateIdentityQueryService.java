package com.lotus.bixi.upms.api.service;

import com.lotus.bixi.upms.api.dto.CandidateIdentity;

/**
 * Transport-neutral lookup of server-owned candidate identity state.
 */
public interface CandidateIdentityQueryService {

    CandidateIdentity findById(Long userId);

}
