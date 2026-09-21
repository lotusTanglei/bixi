package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

abstract class MysqlInboxTestSupport extends MysqlOutboxTestSupport {
    JdbcInboxStore inbox;

    @BeforeEach
    void prepareInbox() {
        Path outboxSchema = Path.of(System.getProperty("outbox.test.schema",
                "../../bixi-project-documents/sql/migrations/20260921_reliable_outbox.sql"));
        new ResourceDatabasePopulator(new FileSystemResource(outboxSchema.resolveSibling("20260921_reliable_inbox.sql"))).execute(dataSource);
        jdbc.execute("TRUNCATE TABLE reliable_inbox");
        jdbc.execute("CREATE TABLE IF NOT EXISTS inbox_test_business (target_owner VARCHAR(64) COLLATE ascii_bin, event_id CHAR(36) COLLATE ascii_bin, value INT NOT NULL, PRIMARY KEY(target_owner,event_id)) ENGINE=InnoDB DEFAULT CHARSET=ascii");
        jdbc.execute("TRUNCATE TABLE inbox_test_business");
        inbox = new JdbcInboxStore(dataSource, manager, properties);
    }

    InboxExecutor executor(DurableMessageHandler handler) {
        return executor(inbox, "workflow", handler);
    }

    InboxExecutor executor(JdbcInboxStore current, String target, DurableMessageHandler handler) {
        return new InboxExecutor(current, target, Map.of(
                new InboxExecutor.Route("upms", "StartRequested", 1), handler,
                new InboxExecutor.Route("other", "StartRequested", 1), handler));
    }

    DurableMessageHandler.Result effect(DurableMessage message) {
        jdbc.update("INSERT INTO inbox_test_business VALUES (?, ?, 1)", message.targetOwner(), message.eventId());
        var reply = DurableMessage.create(message.targetOwner(), message.sourceOwner(),
                UUID.nameUUIDFromBytes((message.targetOwner() + message.eventId()).getBytes(StandardCharsets.UTF_8)).toString(),
                "Started", 1, message.payloadJson());
        store.enqueue(reply, "reply:" + message.eventId(), null, null);
        return DurableMessageHandler.Result.PROCESSED;
    }

    String inboxState(DurableMessage event) {
        return jdbc.queryForObject("SELECT status FROM reliable_inbox WHERE target_owner=? AND event_id=?", String.class, event.targetOwner(), event.eventId());
    }

    void inboxDue() { jdbc.update("UPDATE reliable_inbox SET next_attempt_at=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6))"); }
    void inboxExpired() { jdbc.update("UPDATE reliable_inbox SET lease_until=TIMESTAMPADD(SECOND,-1,UTC_TIMESTAMP(6))"); }
}
