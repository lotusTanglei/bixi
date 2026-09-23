package com.lotus.bixi.common.mybatis.annotation;

public enum DataScopeType {
	ALL("1"),
	DEPT_AND_CHILD("2"),
	DEPT("3"),
	SELF("4");

	private final String code;

	DataScopeType(String code) {
		this.code = code;
	}

	public String code() {
		return code;
	}

	public static DataScopeType fromCode(String code) {
		for (DataScopeType value : values()) if (value.code.equals(code)) return value;
		return SELF;
	}
}
