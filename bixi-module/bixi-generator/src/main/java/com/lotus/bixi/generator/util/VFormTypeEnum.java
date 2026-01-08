package com.lotus.bixi.generator.util;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * vfrom 字段类型
 *
 * @author 唐磊
 * @date 2025-01-01
 */
@Getter
@AllArgsConstructor
public enum VFormTypeEnum {

	GRID("grid"),

	GRID_COL("grid-col");

	private final String type;

}
