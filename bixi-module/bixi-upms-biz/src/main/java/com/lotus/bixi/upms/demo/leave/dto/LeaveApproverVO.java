package com.lotus.bixi.upms.demo.leave.dto;

/** Minimal safe projection for selecting an approver. */
public record LeaveApproverVO(Long id, String name, String username) { }
