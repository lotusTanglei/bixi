package com.lotus.bixi.workflow.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

/** MySQL runs verbatim canonical DDL. H2 adapts only dialect syntax, retaining named constraints. */
final class WorkflowTestSchema {
    static void create(JdbcTemplate jdbc, String... tables) throws Exception {
        String schema = new ClassPathResource("sql/01_init_all_tables.sql").getContentAsString(StandardCharsets.UTF_8);
        boolean mysql;
        try (var connection = jdbc.getDataSource().getConnection()) {
            mysql = "MySQL".equals(connection.getMetaData().getDatabaseProductName());
        }
        for (String table : tables) {
            var matcher = Pattern.compile("CREATE TABLE `" + table + "` \\([\\s\\S]*?\\) ENGINE[^;]*;").matcher(schema);
            assertThat(matcher.find()).as("canonical schema for %s", table).isTrue();
            jdbc.execute("DROP TABLE IF EXISTS " + table);
            String sql = matcher.group();
            if (!mysql) {
                sql = sql.replaceAll(" CHARACTER SET ascii COLLATE ascii_bin", "")
                        .replaceFirst("\\) ENGINE[^;]*;", ")")
                        .replaceAll("UNIQUE KEY `([^`]+)`", "CONSTRAINT `$1` UNIQUE")
                        .replaceAll(",\\s*KEY `[^`]+` \\([^)]*\\)", "");
            }
            jdbc.execute(sql);
        }
    }
}
