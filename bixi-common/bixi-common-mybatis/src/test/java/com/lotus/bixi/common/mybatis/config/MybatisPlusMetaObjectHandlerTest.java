package com.lotus.bixi.common.mybatis.config;

import java.time.LocalDateTime;
import java.util.List;

import com.lotus.bixi.common.mybatis.base.BaseEntity;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusMetaObjectHandlerTest {

    private final MybatisPlusMetaObjectHandler handler = new MybatisPlusMetaObjectHandler();

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void insertPreservesExplicitBusinessAndDatabaseStatuses() {
        TestEntity entity = new TestEntity();
        entity.setStatus("running");
        entity.setDataStatus("1");

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getStatus()).isEqualTo("running");
        assertThat(entity.getDataStatus()).isEqualTo("1");
    }

    @Test
    void insertDefaultsStatusesAndReplacesUntrustedCreationMetadata() {
        authenticate(42L);
        TestEntity entity = new TestEntity();
        entity.setCreateBy(999L);
        entity.setCreateTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        entity.setDelFlag("1");
        LocalDateTime before = LocalDateTime.now();

        handler.insertFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getStatus()).isEqualTo("0");
        assertThat(entity.getDataStatus()).isEqualTo("0");
        assertThat(entity.getCreateBy()).isEqualTo(42L);
        assertThat(entity.getCreateTime()).isBetween(before, LocalDateTime.now());
        assertThat(entity.getDelFlag()).isEqualTo("0");
    }

    @Test
    void updateReplacesUntrustedAuditMetadataWithoutChangingBusinessStatus() {
        authenticate(42L);
        TestEntity entity = new TestEntity();
        entity.setUpdateBy(999L);
        entity.setUpdateTime(LocalDateTime.of(2000, 1, 1, 0, 0));
        entity.setStatus("completed");
        entity.setDataStatus("1");
        LocalDateTime before = LocalDateTime.now();

        handler.updateFill(SystemMetaObject.forObject(entity));

        assertThat(entity.getUpdateBy()).isEqualTo(42L);
        assertThat(entity.getUpdateTime()).isBetween(before, LocalDateTime.now());
        assertThat(entity.getStatus()).isEqualTo("completed");
        assertThat(entity.getDataStatus()).isEqualTo("1");
    }

    private void authenticate(Long id) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new TestPrincipal(id), null, List.of()));
    }

    public static class TestPrincipal {

        private final Long id;

        public TestPrincipal(Long id) {
            this.id = id;
        }

        public Long getId() {
            return id;
        }
    }

    private static class TestEntity extends BaseEntity<TestEntity> {
    }
}
