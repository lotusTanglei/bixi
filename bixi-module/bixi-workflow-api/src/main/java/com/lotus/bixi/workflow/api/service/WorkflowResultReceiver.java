package com.lotus.bixi.workflow.api.service;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** Trusted system callback, separate from authenticated user's workflow operations. */
public interface WorkflowResultReceiver {
    R<Void> receive(@NotNull @Valid WorkflowResultDTO result);
}
