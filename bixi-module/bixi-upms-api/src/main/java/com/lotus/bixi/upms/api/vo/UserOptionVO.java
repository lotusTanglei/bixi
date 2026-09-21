package com.lotus.bixi.upms.api.vo;

/** Minimal recipient selection data; excludes contact details and account security fields. */
public record UserOptionVO(Long id, String username, String name) {
}
