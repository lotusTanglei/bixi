package com.lotus.bixi.common.mybatis.plugins;

import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.annotation.DataScope;
import com.lotus.bixi.common.mybatis.annotation.DataScopeType;
import org.apache.ibatis.builder.StaticSqlSource;
import org.apache.ibatis.executor.statement.RoutingStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Connection;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataScopeInterceptorMatrixTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void selfScopeIgnoresAForgedDepartmentFilter() throws Throwable {
        String sql = rewrite("selectSelf", DataScopeType.SELF);

        assertThat(sql).contains("u.id = 42");
        assertThat(sql).doesNotContain("u.dept_id = 999");
    }

    @Test
    void departmentScopeUsesTheAuthenticatedDepartment() throws Throwable {
        assertThat(rewrite("selectDept", DataScopeType.DEPT)).contains("u.dept_id = 7");
    }

    @Test
    void departmentAndChildrenScopeUsesTheRelationTable() throws Throwable {
        assertThat(rewrite("selectDeptAndChildren", DataScopeType.DEPT_AND_CHILD))
                .contains("u.dept_id IN (SELECT descendant FROM sys_dept_relation WHERE ancestor = 7)");
    }

    @Test
    void allScopeAddsNoOrganizationalPredicate() throws Throwable {
        String sql = rewrite("selectAll", DataScopeType.ALL);

        assertThat(sql).doesNotContain("sys_dept_relation");
        assertThat(sql).doesNotContain("u.id = 42");
    }

    @Test
    void departmentScopeRestrictsUpdatesToTheAuthenticatedDepartment() throws Throwable {
        String sql = rewrite("updateDept", DataScopeType.DEPT,
                "UPDATE sys_user SET email = 'changed'", SqlCommandType.UPDATE);

        assertThat(sql).contains("dept_id = 7");
    }

    @Test
    void selfScopeRestrictsDeletesToTheAuthenticatedUser() throws Throwable {
        String sql = rewrite("deleteSelf", DataScopeType.SELF,
                "DELETE FROM sys_user u", SqlCommandType.DELETE);

        assertThat(sql).contains("id = 42");
    }

    @Test
    void creatorScopeMapsOrganizationRangesThroughTheOwningUser() throws Throwable {
        assertThat(rewrite("selectCreatedBySelf", DataScopeType.SELF,
                "SELECT t.id FROM biz_item t", SqlCommandType.SELECT))
                .contains("create_by = 42");
        assertThat(rewrite("selectCreatedByDept", DataScopeType.DEPT,
                "SELECT t.id FROM biz_item t", SqlCommandType.SELECT))
                .contains("create_by IN (SELECT id FROM sys_user WHERE dept_id = 7)");
    }

    @Test
    void mapperTypeScopeAppliesToInheritedBaseMapperStatements() throws Throwable {
        assertThat(rewrite(TypeScopedMapper.class, "selectList",
                DataScopeType.SELF, "SELECT t.id FROM biz_item t", SqlCommandType.SELECT))
                .contains("create_by = 42");
    }

    private String rewrite(String methodName, DataScopeType scope) throws Throwable {
        return rewrite(methodName, scope,
                "SELECT u.id FROM sys_user u LEFT JOIN sys_dept d ON d.id = u.dept_id", SqlCommandType.SELECT);
    }

    private String rewrite(String methodName, DataScopeType scope, String sql, SqlCommandType commandType)
            throws Throwable {
        return rewrite(ScopedMapper.class, methodName, scope, sql, commandType);
    }

    private String rewrite(Class<?> mapperType, String methodName, DataScopeType scope, String sql,
            SqlCommandType commandType)
            throws Throwable {
        TenantContextHolder.set(7L);
        Principal user = new Principal(42L, 7L, scope.code());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));

        Configuration configuration = new Configuration();
        String mapperId = mapperType.getName() + "." + methodName;
        MappedStatement statement = new MappedStatement.Builder(configuration, mapperId,
                new StaticSqlSource(configuration, sql), commandType).build();
        StatementHandler handler = new RoutingStatementHandler(mock(org.apache.ibatis.executor.Executor.class),
                statement, null, RowBounds.DEFAULT, null, statement.getBoundSql(null));
        Method prepare = StatementHandler.class.getMethod("prepare", Connection.class, Integer.class);
        Invocation invocation = new Invocation(handler, prepare, new Object[]{mock(Connection.class), 0}) {
            @Override
            public Object proceed() {
                return null;
            }
        };

        new DataScopeInterceptor().intercept(invocation);
        return handler.getBoundSql().getSql();
    }

    interface ScopedMapper {
        @DataScope(userAlias = "u", deptAlias = "d")
        Object selectSelf();

        @DataScope(value = DataScopeType.DEPT, userAlias = "u", deptAlias = "d")
        Object selectDept();

        @DataScope(value = DataScopeType.DEPT_AND_CHILD, userAlias = "u", deptAlias = "d")
        Object selectDeptAndChildren();

        @DataScope(value = DataScopeType.ALL, userAlias = "u", deptAlias = "d")
        Object selectAll();

        @DataScope(value = DataScopeType.DEPT, userAlias = "")
        Object updateDept();

        @DataScope(value = DataScopeType.SELF, userAlias = "")
        Object deleteSelf();

        @DataScope(userAlias = "", userColumn = "create_by", creatorScope = true)
        Object selectCreatedBySelf();

        @DataScope(value = DataScopeType.DEPT, userAlias = "", userColumn = "create_by", creatorScope = true)
        Object selectCreatedByDept();
    }

    @DataScope(userAlias = "", userColumn = "create_by", creatorScope = true)
    interface TypeScopedMapper {
        Object selectList();
    }

    public record Principal(Long id, Long deptId, String dataScope) {

        public Long getId() {
            return id;
        }

        public Long getDeptId() {
            return deptId;
        }

        public String getDataScope() {
            return dataScope;
        }
    }
}
