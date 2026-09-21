package com.lotus.bixi.upms.service;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.lotus.bixi.common.core.constant.CacheConstants;
import com.lotus.bixi.upms.service.impl.SysRoleMenuServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Spring cache/transaction proxies and MyBatis writes against H2. */
@SpringJUnitConfig(RoleMenuCacheIntegrationTest.Config.class)
@TestPropertySource(properties = "mybatis-plus.global-config.banner=false")
class RoleMenuCacheIntegrationTest {

    private static final List<String> CACHE_NAMES = List.of(CacheConstants.USER_DETAILS,
            CacheConstants.ROLE_DETAILS, CacheConstants.MENU_DETAILS);
    private static final List<Long> ORIGINAL_MENUS = List.of(10L, 20L);

    @Autowired SysRoleMenuService roleMenus;
    @Autowired CachedAuthorizationReader authorizations;
    @Autowired CachedUserPermissionReader users;
    @Autowired RecordingCacheManager caches;
    @Autowired DataSource source;
    @Autowired PlatformTransactionManager transactionManager;
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedDatabaseAndAuthorizationCaches() {
        jdbc = new JdbcTemplate(source);
        jdbc.execute("DROP TABLE IF EXISTS sys_role_menu");
        jdbc.execute("""
                CREATE TABLE sys_role_menu (
                    role_id BIGINT NOT NULL,
                    menu_id BIGINT NOT NULL,
                    create_time TIMESTAMP,
                    PRIMARY KEY (role_id, menu_id)
                )
                """);
        jdbc.update("INSERT INTO sys_role_menu(role_id, menu_id) VALUES (7, 10), (7, 20), (8, 40)");
        caches.afterClear = name -> {};
        CACHE_NAMES.forEach(name -> caches.getCache(name).clear());
        caches.clears.clear();
        assertThat(AopUtils.isAopProxy(roleMenus)).isTrue();
        assertThat(AopUtils.isAopProxy(authorizations)).isTrue();
        assertThat(AopUtils.isAopProxy(users)).isTrue();
        assertAuthorizationMenus(ORIGINAL_MENUS);
        assertThat(caches.getCache(CacheConstants.MENU_DETAILS).get("7:true")).isNotNull();
        assertThat(caches.getCache(CacheConstants.MENU_DETAILS).get("7:false")).isNotNull();
    }

    @Test
    void revokingEveryMenuClearsAllAuthorizationCachesAfterCommit() {
        assertThatCode(() -> assertThat(roleMenus.saveRoleMenus(7L, "")).isTrue())
                .doesNotThrowAnyException();

        assertCommittedChange(List.of());
    }

    @Test
    void replacingSomeMenusClearsAllAuthorizationCachesAfterCommit() {
        assertThatCode(() -> assertThat(roleMenus.saveRoleMenus(7L, "20,30")).isTrue())
                .doesNotThrowAnyException();

        assertCommittedChange(List.of(20L, 30L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "20,30"})
    void userCacheReloadedBetweenEvictionsDoesNotRetainRevokedMenus(String menuIds) {
        var expected = expectedMenus(menuIds);
        var readsBetweenEvictions = new ArrayList<List<Long>>();
        // A user-cache miss loads permissions through the separately proxied menu cache,
        // matching UserDetailsService -> findUserInfo -> findMenuByRoleId.
        caches.afterClear = name -> readsBetweenEvictions.add(users.userMenus("user-7"));

        assertThat(roleMenus.saveRoleMenus(7L, menuIds)).isTrue();

        assertThat(caches.clears).allSatisfy(clear -> assertThat(clear.committedMenus()).isEqualTo(expected));
        assertThat(readsBetweenEvictions).hasSize(3);
        assertThat(readsBetweenEvictions.get(2)).isEqualTo(expected);
        assertThat(users.userMenus("user-7")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "20,30"})
    void keepsCachesUntilTheOuterTransactionCommits(String menuIds) {
        var expected = expectedMenus(menuIds);
        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(roleMenus.saveRoleMenus(7L, menuIds)).isTrue();
            assertThat(databaseMenus()).isEqualTo(expected);
            assertThat(committedMenus(source)).isEqualTo(ORIGINAL_MENUS);
            assertThat(caches.clears).isEmpty();
            assertAuthorizationMenus(ORIGINAL_MENUS);
        })).doesNotThrowAnyException();

        assertCommittedChange(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "20,30"})
    void outerRollbackPreservesDatabaseAndAuthorizationCaches(String menuIds) {
        assertThatCode(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(roleMenus.saveRoleMenus(7L, menuIds)).isTrue();
            assertThat(databaseMenus()).isEqualTo(expectedMenus(menuIds));
            assertThat(caches.clears).isEmpty();
            assertAuthorizationMenus(ORIGINAL_MENUS);
            status.setRollbackOnly();
        })).doesNotThrowAnyException();

        assertThat(databaseMenus()).isEqualTo(ORIGINAL_MENUS);
        assertThat(caches.clears).isEmpty();
        assertAuthorizationMenus(ORIGINAL_MENUS);
    }

    @Test
    void failedDatabaseWriteRollsBackRemovalAndPreservesAuthorizationCaches() {
        // The real composite primary key rejects the second insert in the batch.
        assertThatThrownBy(() -> roleMenus.saveRoleMenus(7L, "20,20"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(databaseMenus()).isEqualTo(ORIGINAL_MENUS);
        assertThat(caches.clears).isEmpty();
        assertAuthorizationMenus(ORIGINAL_MENUS);
    }

    private void assertCommittedChange(List<Long> expected) {
        assertThat(databaseMenus()).isEqualTo(expected);
        assertThat(jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=8", Long.class))
                .containsExactly(40L);
        assertThat(caches.clears).extracting(CacheClear::name).containsExactlyInAnyOrderElementsOf(CACHE_NAMES);
        assertThat(caches.clears).allSatisfy(clear -> assertThat(clear.committedMenus()).isEqualTo(expected));
        CACHE_NAMES.forEach(name -> assertThat((Map<?, ?>) caches.getCache(name).getNativeCache()).isEmpty());
        assertAuthorizationMenus(expected);
    }

    private void assertAuthorizationMenus(List<Long> expected) {
        assertThat(users.userMenus("user-7")).isEqualTo(expected);
        assertThat(authorizations.roleMenus("7")).isEqualTo(expected);
        assertThat(authorizations.menuDetails(7L, true)).isEqualTo(expected);
        assertThat(authorizations.menuDetails(7L, false)).isEqualTo(expected);
    }

    private List<Long> databaseMenus() {
        return jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=7 ORDER BY menu_id", Long.class);
    }

    private static List<Long> expectedMenus(String menuIds) {
        return menuIds.isEmpty() ? List.of() : List.of(20L, 30L);
    }

    private static List<Long> committedMenus(DataSource source) {
        // Bypass Spring's transaction-bound connection so eviction observes only committed rows.
        try (var connection = source.getConnection();
             var statement = connection.prepareStatement("SELECT menu_id FROM sys_role_menu WHERE role_id=7 ORDER BY menu_id");
             var rows = statement.executeQuery()) {
            var menus = new ArrayList<Long>();
            while (rows.next()) {
                menus.add(rows.getLong(1));
            }
            return menus;
        }
        catch (SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record CacheClear(String name, List<Long> committedMenus) {}

    static class RecordingCacheManager extends ConcurrentMapCacheManager {
        private final DataSource source;
        private final List<CacheClear> clears = new ArrayList<>();
        private Consumer<String> afterClear = name -> {};

        RecordingCacheManager(DataSource source) {
            this.source = source;
            setCacheNames(CACHE_NAMES);
        }

        @Override
        protected Cache createConcurrentMapCache(String name) {
            return new ConcurrentMapCache(name) {
                @Override
                public void clear() {
                    clears.add(new CacheClear(name, committedMenus(source)));
                    super.clear();
                    afterClear.accept(name);
                }
            };
        }
    }

    static class CachedUserPermissionReader {
        private final CachedAuthorizationReader authorizations;

        CachedUserPermissionReader(CachedAuthorizationReader authorizations) {
            this.authorizations = authorizations;
        }

        @Cacheable(cacheNames = CacheConstants.USER_DETAILS, key = "#p0")
        public List<Long> userMenus(String username) {
            return authorizations.menuDetails(7L, true);
        }
    }

    static class CachedAuthorizationReader {
        private final JdbcTemplate jdbc;

        CachedAuthorizationReader(DataSource source) {
            jdbc = new JdbcTemplate(source);
        }

        @Cacheable(cacheNames = CacheConstants.ROLE_DETAILS, key = "#p0")
        public List<Long> roleMenus(String roleIds) {
            return readMenus(Long.valueOf(roleIds));
        }

        @Cacheable(cacheNames = CacheConstants.MENU_DETAILS, key = "#p0 + ':' + #p1")
        public List<Long> menuDetails(Long roleId, boolean workflowEnabled) {
            return readMenus(roleId);
        }

        private List<Long> readMenus(Long roleId) {
            return jdbc.queryForList("SELECT menu_id FROM sys_role_menu WHERE role_id=? ORDER BY menu_id", Long.class, roleId);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    @EnableTransactionManagement
    @Import(SysRoleMenuServiceImpl.class)
    @MapperScan("com.lotus.bixi.upms.mapper")
    @ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
    static class Config {
        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:role-menu-cache-" + UUID.randomUUID()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        }

        @Bean
        DataSourceTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }

        @Bean
        RecordingCacheManager cacheManager(DataSource source) {
            return new RecordingCacheManager(source);
        }

        @Bean
        CachedAuthorizationReader authorizations(DataSource source) {
            return new CachedAuthorizationReader(source);
        }

        @Bean
        CachedUserPermissionReader users(CachedAuthorizationReader authorizations) {
            return new CachedUserPermissionReader(authorizations);
        }
    }
}
