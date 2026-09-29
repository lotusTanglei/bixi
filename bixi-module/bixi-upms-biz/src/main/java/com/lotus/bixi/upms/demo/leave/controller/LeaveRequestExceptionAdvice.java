package com.lotus.bixi.upms.demo.leave.controller;

import com.lotus.bixi.common.core.util.R;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Keeps the leave HTTP contract deterministic when a required command identity is absent. */
@Order(-100)
@RestControllerAdvice(assignableTypes = LeaveRequestController.class)
public class LeaveRequestExceptionAdvice {
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<R<Void>> missingParameter(MissingServletRequestParameterException error) {
        return ResponseEntity.badRequest().body(R.failed("缺少必填参数: " + error.getParameterName()));
    }
}
