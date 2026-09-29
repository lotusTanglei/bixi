package com.lotus.bixi.upms.mapper;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.context.TenantContextHolder;
import com.lotus.bixi.common.mybatis.MybatisAutoConfiguration;
import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.upms.api.entity.SysUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.apache.ibatis.session.SqlSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies generated MyBatis-Plus user writes honor tenant and organization scope. */
@SpringJUnitConfig(SysUserMapperDataScopeIntegrationTest.Config.class)
class SysUserMapperDataScopeIntegrationTest {

    @Autowired SysUserMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(1L);
        BixiUser user = new BixiUser(100L, 10L, 1L, "scope-user", "unused", null,
                true, true, true, true, List.of());
        user.setDataScope("3");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));

        jdbc.execute("DROP TABLE IF EXISTS sys_user");
        jdbc.execute("""
                CREATE TABLE sys_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(64), password VARCHAR(255), salt VARCHAR(255),
                    phone VARCHAR(20), avatar VARCHAR(255), nickname VARCHAR(64), name VARCHAR(64),
                    email VARCHAR(128), dept_id BIGINT, lock_flag VARCHAR(1),
                    wx_openid VARCHAR(32), mini_openid VARCHAR(32), qq_openid VARCHAR(32),
                    gitee_login VARCHAR(100), osc_id VARCHAR(100), create_by BIGINT, update_by BIGINT,
                    create_time TIMESTAMP, update_time TIMESTAMP, del_flag VARCHAR(1), status VARCHAR(1),
                    data_status VARCHAR(1), tenant_id BIGINT, remark VARCHAR(500)
                )
                """);
        insert(1L, "same-tenant-same-dept", 10L, 1L);
        insert(2L, "same-tenant-other-dept", 20L, 1L);
        insert(3L, "other-tenant-same-dept", 10L, 2L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContextHolder.clear();
    }

    @Test
    void generatedUpdateAndLogicalDeleteAreScopedByTenantAndDepartment() {
        assertThat(sqlSessionFactory.getConfiguration().getInterceptors())
                .anyMatch(interceptor -> interceptor instanceof com.lotus.bixi.common.mybatis.plugins.DataScopeInterceptor);

        SysUser inScope = new SysUser();
        inScope.setId(1L);
        inScope.setName("updated");
        assertThat(mapper.updateById(inScope)).isEqualTo(1);

        SysUser otherDepartment = new SysUser();
        otherDepartment.setId(2L);
        otherDepartment.setName("must-not-update");
        assertThat(mapper.updateById(otherDepartment)).isZero();

        assertThat(mapper.deleteById(1L)).isEqualTo(1);
        assertThat(mapper.deleteById(2L)).isZero();
        assertThat(mapper.deleteById(3L)).isZero();

        assertThat(jdbc.queryForObject("SELECT name FROM sys_user WHERE id = 1", String.class))
                .isEqualTo("updated");
        assertThat(jdbc.queryForObject("SELECT del_flag FROM sys_user WHERE id = 1", String.class))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject("SELECT name FROM sys_user WHERE id = 2", String.class))
                .isEqualTo("same-tenant-other-dept");
        assertThat(jdbc.queryForObject("SELECT del_flag FROM sys_user WHERE id = 3", String.class))
                .isEqualTo("0");
    }

    private void insert(long id, String name, long deptId, long tenantId) {
        jdbc.update("""
                INSERT INTO sys_user (id, username, name, dept_id, create_time, update_time,
                    del_flag, status, data_status, tenant_id)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, '0', '0', '0', ?)
                """, id, "user-" + id, name, deptId, tenantId);
    }

    @Configuration(proxyBeanMethods = false)
    @Import({MybatisAutoConfiguration.class})
    @MapperScan("com.lotus.bixi.upms.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:sys-user-scope-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }
    }
}
