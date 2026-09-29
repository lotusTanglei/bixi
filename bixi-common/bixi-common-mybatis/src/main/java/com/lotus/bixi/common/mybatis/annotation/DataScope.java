package com.lotus.bixi.common.mybatis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {
	DataScopeType value() default DataScopeType.SELF;
	String deptAlias() default "dept";
	String userAlias() default "user";

	/**
	 * Column used for the authenticated user predicate. Existing callers keep the
	 * historical {@code id} default; generated records can point this at their
	 * immutable creator column.
	 */
	String userColumn() default "id";

	/**
	 * Column used for a direct department predicate when creatorScope is false.
	 */
	String deptColumn() default "dept_id";

	/**
	 * Resolve department ranges through the creator's sys_user row. This lets
	 * generated tables inherit organization scope from BaseEntity.createBy even
	 * when they do not carry a denormalized dept_id column.
	 */
	boolean creatorScope() default false;
}
