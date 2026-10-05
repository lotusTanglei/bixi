package com.lotus.bixi.ai.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keeps the AI tables compatible with the inherited BaseEntity mapping.
 *
 * MyBatis-Plus maps BaseEntity's audit/status properties into every AI entity,
 * so a canonical schema that omits one of those columns fails at runtime even
 * though a hand-written test schema may still pass.
 */
class AiSchemaContractTest {

    private static final Map<String, List<String>> REQUIRED_COLUMNS_BY_TABLE = Map.of(
            "ai_model_config", List.of("id", "current_model", "temperature", "max_tokens", "top_p",
                    "system_prompt", "create_by", "update_by", "create_time", "update_time", "del_flag",
                    "status", "data_status", "tenant_id", "remark"),
            "ai_session", List.of("id", "create_by", "update_by", "create_time", "update_time", "del_flag",
                    "status", "data_status", "tenant_id", "remark"),
            "ai_message", List.of("id", "session_id", "role", "content", "sources", "create_by", "update_by",
                    "create_time", "update_time", "del_flag", "status", "data_status", "tenant_id", "remark"),
            "ai_conversation", List.of("id", "session_id", "question", "answer", "model", "conversation_type",
                    "user_id", "create_by", "update_by", "create_time", "update_time", "del_flag", "status",
                    "data_status", "tenant_id", "remark"),
            "ai_document", List.of("id", "title", "content", "source", "doc_type", "vector_status", "user_id",
                    "create_by", "update_by", "create_time", "update_time", "del_flag", "status", "data_status",
                    "tenant_id", "remark"),
            "ai_embedding", List.of("id", "document_id", "vector_id", "embedding_model", "embedding", "dimension",
                    "chunk_index", "chunk_content", "create_by", "update_by", "create_time", "update_time",
                    "del_flag", "status", "data_status", "tenant_id", "remark"));

    @Test
    void canonicalAiSchemasContainEveryBaseEntityColumn() throws IOException {
        assertSchema(Path.of("bixi-project-documents", "sql", "01_schema.sql"));
        assertSchema(Path.of("bixi-project-documents", "sql", "bixi_ai.sql"));
    }

    @Test
    void existingDatabaseMigrationAddsTheSameInheritedColumnsIdempotently() throws IOException {
        String migration = Files.readString(locate(Path.of("bixi-project-documents", "sql", "migrations",
                "20260926_ai_base_entity_columns.sql")));
        assertThat(migration)
                .contains("DROP PROCEDURE IF EXISTS")
                .contains("CALL bixi_migrate_ai_base_entity_columns_20260926()")
                .contains("information_schema.columns");
        assertThat(migration)
                .contains("ALTER TABLE ai_session ADD COLUMN data_status")
                .contains("ALTER TABLE ai_session ADD COLUMN remark")
                .contains("ALTER TABLE ai_message ADD COLUMN status")
                .contains("ALTER TABLE ai_message ADD COLUMN data_status")
                .contains("ALTER TABLE ai_message ADD COLUMN remark")
                .contains("ALTER TABLE ai_conversation ADD COLUMN status")
                .contains("ALTER TABLE ai_conversation ADD COLUMN data_status")
                .contains("ALTER TABLE ai_conversation ADD COLUMN remark")
                .contains("ALTER TABLE ai_document ADD COLUMN status")
                .contains("ALTER TABLE ai_document ADD COLUMN data_status")
                .contains("ALTER TABLE ai_document ADD COLUMN remark")
                .contains("ALTER TABLE ai_embedding ADD COLUMN status")
                .contains("ALTER TABLE ai_embedding ADD COLUMN data_status")
                .contains("ALTER TABLE ai_embedding ADD COLUMN remark");

        String ingestionMigration = Files.readString(locate(Path.of("bixi-project-documents", "sql", "migrations",
                "20260926_ai_rag_ingestion.sql")));
        assertThat(ingestionMigration)
                .contains("ALTER TABLE ai_embedding ADD COLUMN embedding")
                .contains("ALTER TABLE ai_embedding ADD COLUMN chunk_content")
                .contains("DROP PROCEDURE IF EXISTS")
                .contains("CALL bixi_migrate_ai_rag_ingestion_20260926()");

        String modelConfigMigration = Files.readString(locate(Path.of("bixi-project-documents", "sql", "migrations",
                "20260928_ai_model_config.sql")));
        assertThat(modelConfigMigration)
                .contains("CREATE TABLE ai_model_config")
                .contains("uk_ai_model_config_tenant")
                .contains("tenant_id")
                .contains("DROP PROCEDURE IF EXISTS")
                .contains("CALL bixi_migrate_ai_model_config_20260928()");
    }

    @Test
    void modelConfigMigrationProtectsTenantAndBaseEntityContract() throws IOException {
        String migration = Files.readString(locate(Path.of("bixi-project-documents", "sql", "migrations",
                "20260928_ai_model_config.sql")));

        // A nullable or differently typed tenant key would let rows escape the
        // tenant interceptor, so an existing table must be checked before it is
        // accepted by the application.
        assertThat(migration)
                .contains("column_name = 'tenant_id' AND data_type = 'bigint'")
                .contains("column_name = 'tenant_id' AND is_nullable = 'NO'")
                .contains("MODIFY COLUMN tenant_id BIGINT NOT NULL")
                .contains("duplicate_count")
                .contains("information_schema.statistics");

        for (String column : List.of("create_by", "update_by", "create_time", "update_time",
                "del_flag", "status", "data_status", "remark")) {
            assertThat(migration)
                    .as("existing-table migration must account for BaseEntity.%s", column)
                    .contains("column_name = '" + column + "'")
                    .contains("ADD COLUMN " + column);
        }
    }

    private static void assertSchema(Path relativePath) throws IOException {
        Path path = locate(relativePath);
        String schema = Files.readString(path);
        for (Map.Entry<String, List<String>> entry : REQUIRED_COLUMNS_BY_TABLE.entrySet()) {
            String table = entry.getKey();
            String ddl = tableDdl(schema, table);
            for (String column : entry.getValue()) {
                assertThat(ddl)
                        .as("%s must define %s for the AI entity mapper", table, column)
                        .contains("`" + column + "`");
            }
            if ("ai_embedding".equals(table)) {
                assertThat(ddl)
                        .as("%s must index document chunks for retrieval", table)
                        .contains("idx_embedding_document_chunk");
            }
            if ("ai_model_config".equals(table)) {
                assertThat(ddl)
                        .as("%s must be unique per tenant", table)
                        .contains("uk_ai_model_config_tenant")
                        .contains("tenant_id");
                assertThat(ddl)
                        .as("%s must make tenant ownership mandatory", table)
                        .containsPattern("(?is)`tenant_id`\\s+BIGINT\\s+NOT NULL");
            }
        }
    }

    private static String tableDdl(String schema, String table) {
        Pattern pattern = Pattern.compile(
                "(?s)CREATE TABLE `" + Pattern.quote(table) + "` \\((.*?)\\) ENGINE");
        Matcher matcher = pattern.matcher(schema);
        assertThat(matcher.find()).as("canonical schema must define %s", table).isTrue();
        return matcher.group(1);
    }

    private static Path locate(Path relativePath) {
        Path current = Path.of(System.getProperty("user.dir"));
        for (Path candidate : List.of(current.resolve(relativePath),
                current.resolve("../..").resolve(relativePath),
                current.resolve("../../..").resolve(relativePath))) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }
        throw new IllegalStateException("Cannot locate " + relativePath + " from " + current);
    }
}
