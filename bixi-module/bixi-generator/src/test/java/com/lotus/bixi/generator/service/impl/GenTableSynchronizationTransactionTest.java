package com.lotus.bixi.generator.service.impl;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lotus.bixi.generator.config.BixiGeneratorDefaultProperties;
import com.lotus.bixi.generator.entity.GenTable;
import com.lotus.bixi.generator.entity.GenTableColumn;
import com.lotus.bixi.generator.mapper.GenTableMapper;
import com.lotus.bixi.generator.service.GenGroupService;
import com.lotus.bixi.generator.service.GenTableColumnService;
import com.lotus.bixi.generator.service.GenTableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(GenTableSynchronizationTransactionTest.Config.class)
class GenTableSynchronizationTransactionTest {

	@Autowired GenTableService service;
	@Autowired GenTableColumnService columnService;
	@Autowired JdbcTemplate jdbc;
	@Autowired SyncBarrier syncBarrier;

	@BeforeEach
	void createSchemaAndFailure() {
		syncBarrier.reset();
		jdbc.execute("DROP TABLE IF EXISTS gen_table_column");
		jdbc.execute("DROP TABLE IF EXISTS gen_table");
		jdbc.execute("""
				CREATE TABLE gen_table (
				  id BIGINT PRIMARY KEY,
				  ds_name VARCHAR(200), db_type VARCHAR(200), table_name VARCHAR(200),
				  class_name VARCHAR(200), table_comment VARCHAR(200), author VARCHAR(200), email VARCHAR(200),
				  package_name VARCHAR(200), version VARCHAR(200), generator_type CHAR(1),
				  backend_path VARCHAR(500), frontend_path VARCHAR(500), module_name VARCHAR(200),
				  function_name VARCHAR(200), form_layout TINYINT, baseclass_id BIGINT, style BIGINT,
				  child_table_name VARCHAR(200), main_field VARCHAR(200), child_field VARCHAR(200),
				  create_by BIGINT, update_by BIGINT, create_time TIMESTAMP, update_time TIMESTAMP,
				  del_flag CHAR(1) DEFAULT '0', status CHAR(1), data_status CHAR(1), tenant_id BIGINT,
				  remark VARCHAR(500), UNIQUE (table_name, ds_name)
				)
				""");
		jdbc.execute("""
				CREATE TABLE gen_table_column (
				  id BIGINT AUTO_INCREMENT PRIMARY KEY, ds_name VARCHAR(200), table_name VARCHAR(200),
				  field_name VARCHAR(200), field_type VARCHAR(200), field_comment VARCHAR(200),
				  del_flag CHAR(1) DEFAULT '0'
				)
				""");
		jdbc.update("""
				INSERT INTO gen_table
				(id, ds_name, db_type, table_name, class_name, table_comment, author, package_name,
				 module_name, function_name, style, child_table_name, main_field, child_field,
				 create_by, create_time, del_flag, remark)
				VALUES (41, 'master', 'old-db', 'purchase_order', 'PurchaseOrderCustom', 'old comment',
				 'generator-owner', 'com.example.orders', 'purchasing', 'purchaseOrderAggregate', 88,
				 'purchase_order_item', 'id', 'order_id', 7, TIMESTAMP '2026-09-01 08:30:00', '0', 'keep this note')
				""");
		jdbc.update("""
				INSERT INTO gen_table_column (id, ds_name, table_name, field_name, field_type, field_comment, del_flag)
				VALUES (11, 'master', 'purchase_order', 'shared_field', 'VARCHAR', 'old shared', '0'),
				       (12, 'master', 'purchase_order', 'dropped_field', 'VARCHAR', 'dropped', '0')
				""");

		reset(columnService);
		GenTableColumn shared = databaseColumn(11L, "shared_field", "VARCHAR", "old shared");
		GenTableColumn dropped = databaseColumn(12L, "dropped_field", "VARCHAR", "dropped");
		when(columnService.list(any(Wrapper.class))).thenReturn(List.of(shared, dropped));
		when(columnService.updatePhysicalMetadataById(any(GenTableColumn.class))).thenAnswer(invocation -> {
			GenTableColumn column = invocation.getArgument(0);
			return jdbc.update("""
					UPDATE gen_table_column SET field_type = ?, field_comment = ? WHERE id = ?
					""", column.getFieldType(), column.getFieldComment(), column.getId()) > 0;
		});
		doAnswer(invocation -> jdbc.update("""
				UPDATE gen_table_column SET del_flag = '1'
				WHERE id = 12 AND del_flag = '0'
				""") > 0).when(columnService).removeByIds(anyCollection());
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Collection<GenTableColumn> columns = invocation.getArgument(0);
			GenTableColumn column = columns.iterator().next();
			jdbc.update("""
					INSERT INTO gen_table_column
					(ds_name, table_name, field_name, field_type, field_comment, del_flag)
					VALUES (?, ?, ?, ?, ?, '0')
					""", column.getDsName(), column.getTableName(), column.getFieldName(),
					column.getFieldType(), column.getFieldComment());
			throw new RuntimeException("column write failed");
		}).when(columnService).saveBatch(anyCollection());
	}

	@Test
	void concurrentSynchronizationSerializesBeforeReadingColumnsAndInsertsEachPhysicalColumnOnce() throws Exception {
		reset(columnService);
		doNothing().when(columnService).initFieldList(any());
		when(columnService.list(any(Wrapper.class))).thenAnswer(invocation -> jdbc.query("""
				SELECT id, ds_name, table_name, field_name, field_type, field_comment
				FROM gen_table_column WHERE ds_name = 'master' AND table_name = 'purchase_order' AND del_flag = '0'
				ORDER BY id
				""", (resultSet, row) -> databaseColumn(resultSet.getLong("id"),
				resultSet.getString("field_name"), resultSet.getString("field_type"),
				resultSet.getString("field_comment"))));
		when(columnService.updatePhysicalMetadataById(any(GenTableColumn.class))).thenAnswer(invocation -> {
			GenTableColumn column = invocation.getArgument(0);
			return jdbc.update("UPDATE gen_table_column SET field_type = ?, field_comment = ? WHERE id = ?",
				column.getFieldType(), column.getFieldComment(), column.getId()) > 0;
		});
		doAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Collection<Long> ids = invocation.getArgument(0);
			for (Long id : ids) {
				if (jdbc.update("UPDATE gen_table_column SET del_flag = '1' WHERE id = ? AND del_flag = '0'", id) == 0) {
					return false;
				}
			}
			return true;
		}).when(columnService).removeByIds(anyCollection());
		when(columnService.saveBatch(anyCollection())).thenAnswer(invocation -> {
			@SuppressWarnings("unchecked")
			Collection<GenTableColumn> columns = invocation.getArgument(0);
			for (GenTableColumn column : columns) {
				jdbc.update("""
						INSERT INTO gen_table_column
						(ds_name, table_name, field_name, field_type, field_comment, del_flag)
						VALUES (?, ?, ?, ?, ?, '0')
						""", column.getDsName(), column.getTableName(), column.getFieldName(),
					column.getFieldType(), column.getFieldComment());
			}
			return true;
		});

		syncBarrier.enable();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch secondStarted = new CountDownLatch(1);
		try {
			Future<GenTable> first = executor.submit(() -> service.syncTable("master", "purchase_order"));
			assertThat(syncBarrier.firstMetadata.await(5, TimeUnit.SECONDS)).isTrue();
			Future<GenTable> second = executor.submit(() -> {
				secondStarted.countDown();
				return service.syncTable("master", "purchase_order");
			});
			assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThat(syncBarrier.secondMetadata.await(300, TimeUnit.MILLISECONDS))
				.as("the second transaction must wait for the configuration row lock")
				.isFalse();

			syncBarrier.releaseFirst.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS).getFieldList()).hasSize(2);
			assertThat(second.get(5, TimeUnit.SECONDS).getFieldList()).hasSize(2);
		}
		finally {
			syncBarrier.releaseFirst.countDown();
			executor.shutdownNow();
		}

		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM gen_table_column WHERE field_name = 'new_field' AND del_flag = '0'
				""", Integer.class)).isEqualTo(1);
		verify(columnService, times(1)).saveBatch(anyCollection());
	}

	@Test
	void insertFailureRollsBackTableMetadataColumnUpdateAndDroppedColumnDelete() {
		assertThatThrownBy(() -> service.syncTable("master", "purchase_order"))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("column write failed");

		assertThat(jdbc.queryForObject("SELECT table_comment FROM gen_table WHERE id = 41", String.class))
			.isEqualTo("old comment");
		assertThat(jdbc.queryForObject("SELECT db_type FROM gen_table WHERE id = 41", String.class))
			.isEqualTo("old-db");
		assertThat(jdbc.queryForObject("SELECT field_type FROM gen_table_column WHERE id = 11", String.class))
			.isEqualTo("VARCHAR");
		assertThat(jdbc.queryForObject("SELECT field_comment FROM gen_table_column WHERE id = 11", String.class))
			.isEqualTo("old shared");
		assertThat(jdbc.queryForObject("SELECT del_flag FROM gen_table_column WHERE id = 12", String.class))
			.isEqualTo("0");
		assertThat(jdbc.queryForObject("""
				SELECT COUNT(*) FROM gen_table_column WHERE field_name = 'new_field'
				""", Integer.class)).isZero();
		verify(columnService).updatePhysicalMetadataById(any(GenTableColumn.class));
		verify(columnService).removeByIds(anyCollection());
		verify(columnService).saveBatch(anyCollection());
	}

	@Test
	void initializationFailureRollsBackTableMetadataWithoutChangingColumns() {
		RuntimeException failure = new RuntimeException("field initialization failed");
		doThrow(failure).when(columnService).initFieldList(any());

		assertThatThrownBy(() -> service.syncTable("master", "purchase_order")).isSameAs(failure);

		assertThat(jdbc.queryForObject("SELECT table_comment FROM gen_table WHERE id = 41", String.class))
			.isEqualTo("old comment");
		assertThat(jdbc.queryForObject("SELECT db_type FROM gen_table WHERE id = 41", String.class))
			.isEqualTo("old-db");
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM gen_table_column WHERE del_flag = '0'", Integer.class))
			.isEqualTo(2);
		verify(columnService, never()).updatePhysicalMetadataById(any());
		verify(columnService, never()).removeByIds(anyCollection());
		verify(columnService, never()).saveBatch(anyCollection());
	}

	private static GenTableColumn databaseColumn(long id, String name, String type, String comment) {
		GenTableColumn column = new GenTableColumn();
		column.setId(id);
		column.setDsName("master");
		column.setTableName("purchase_order");
		column.setFieldName(name);
		column.setFieldType(type);
		column.setFieldComment(comment);
		return column;
	}

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@MapperScan(basePackageClasses = GenTableMapper.class)
	@ImportAutoConfiguration(MybatisPlusAutoConfiguration.class)
	static class Config {

		@Bean DataSource dataSource() {
			return new DriverManagerDataSource("jdbc:h2:mem:generator-table-sync-" + UUID.randomUUID()
				+ ";MODE=MYSQL;DB_CLOSE_DELAY=-1", "sa", "");
		}

		@Bean JdbcTemplate jdbcTemplate(DataSource dataSource) {
			return new JdbcTemplate(dataSource);
		}

		@Bean DataSourceTransactionManager transactionManager(DataSource dataSource) {
			return new DataSourceTransactionManager(dataSource);
		}

		@Bean GenTableColumnService columnService() {
			return mock(GenTableColumnService.class);
		}

		@Bean GenGroupService groupService() {
			return mock(GenGroupService.class);
		}

		@Bean SyncBarrier syncBarrier() {
			return new SyncBarrier();
		}

		@Bean GenTableService tableService(GenTableMapper mapper, GenTableColumnService columnService,
				GenGroupService groupService, SyncBarrier syncBarrier) {
			GenTableServiceImpl service = new GenTableServiceImpl(
				new BixiGeneratorDefaultProperties(), columnService, groupService) {
				@Override
				protected TableMetadataSnapshot loadTableMetadata(String dsName, String tableName) {
					syncBarrier.coordinate();
					GenTableColumn column = new GenTableColumn();
					column.setDsName(dsName);
					column.setTableName(tableName);
					column.setFieldName("shared_field");
					column.setFieldType("BIGINT");
					column.setFieldComment("current shared");
					GenTableColumn added = new GenTableColumn();
					added.setDsName(dsName);
					added.setTableName(tableName);
					added.setFieldName("new_field");
					added.setFieldType("BIGINT");
					added.setFieldComment("new");
					return new TableMetadataSnapshot("current physical comment", "MySQL", List.of(column, added));
				}
			};
			ReflectionTestUtils.setField(service, "baseMapper", mapper);
			return service;
		}

	}

	static final class SyncBarrier {

		private final AtomicInteger metadataCalls = new AtomicInteger();
		private volatile boolean enabled;
		private CountDownLatch firstMetadata;
		private CountDownLatch secondMetadata;
		private CountDownLatch releaseFirst;

		void reset() {
			enabled = false;
			metadataCalls.set(0);
			firstMetadata = new CountDownLatch(1);
			secondMetadata = new CountDownLatch(1);
			releaseFirst = new CountDownLatch(1);
		}

		void enable() {
			enabled = true;
		}

		void coordinate() {
			if (!enabled) return;
			int call = metadataCalls.incrementAndGet();
			if (call == 1) {
				firstMetadata.countDown();
				try {
					if (!releaseFirst.await(5, TimeUnit.SECONDS)) {
						throw new IllegalStateException("timed out waiting to release the first synchronization");
					}
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("synchronization barrier interrupted", interrupted);
				}
			}
			else if (call == 2) {
				secondMetadata.countDown();
			}
		}

	}

}
