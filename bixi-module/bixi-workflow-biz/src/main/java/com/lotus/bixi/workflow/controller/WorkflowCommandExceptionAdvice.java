package com.lotus.bixi.workflow.controller;

import com.lotus.bixi.common.core.util.R;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.exception.WorkflowCommandNotFoundException;
import com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException;
import com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException;
import com.lotus.bixi.workflow.api.json.WorkflowInvalidUnicodeException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

/** Restricted to workflow HTTP endpoints; common exception policy is unchanged. */
@Order(-100)
@ConditionalOnWorkflowEnabled
@RestControllerAdvice(basePackages = "com.lotus.bixi.workflow.controller")
public class WorkflowCommandExceptionAdvice {
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> invalidJson(HttpMessageNotReadableException error) {
        return ResponseEntity.badRequest().body(R.failed("无效的JSON内容"));
    }
    @ExceptionHandler(WorkflowInvalidUnicodeException.class)
    public ResponseEntity<R<Void>> invalidUnicode(WorkflowInvalidUnicodeException error) {
        return ResponseEntity.badRequest().body(R.failed(error.getMessage()));
    }
    @ExceptionHandler(WorkflowRequestConflictException.class)
    public ResponseEntity<R<Map<String, String>>> conflict(WorkflowRequestConflictException error) {
        return ResponseEntity.status(409).body(R.failed(Map.of("errorCode", WorkflowRequestConflictException.ERROR_CODE,
                "requestId", error.getRequestId()), error.getMessage()));
    }
    @ExceptionHandler(WorkflowOperationConflictException.class)
    public ResponseEntity<R<Map<String, String>>> operationConflict(WorkflowOperationConflictException error) {
        return ResponseEntity.status(409).body(R.failed(Map.of("errorCode", WorkflowOperationConflictException.ERROR_CODE,
                "requestId", error.getRequestId()), error.getMessage()));
    }
    @ExceptionHandler(WorkflowCommandNotFoundException.class)
    public ResponseEntity<R<Map<String, String>>> notFound(WorkflowCommandNotFoundException error) {
        return ResponseEntity.status(404).body(R.failed(Map.of("errorCode", WorkflowCommandNotFoundException.ERROR_CODE,
                "requestId", error.getRequestId()), error.getMessage()));
    }
}
