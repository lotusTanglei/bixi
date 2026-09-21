package com.lotus.bixi.common.mq.reliable;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class DurableMessageTest {
    @Test
    void canonicalPayloadPreservesLargeIntegersAndFractionalPrecision() {
        String id = UUID.randomUUID().toString();
        var first = DurableMessage.create("upms", "workflow", id, "StartRequested", 1,
                "{\"z\":9007199254740993123456789,\"a\":{\"n\":1.2345678901234567890123456789,\"b\":1.00}}");
        var reordered = DurableMessage.create("upms", "workflow", id, "StartRequested", 1,
                "{\"a\":{\"b\":1,\"n\":1.2345678901234567890123456789},\"z\":9007199254740993123456789}");
        assertThat(first).isEqualTo(reordered);
        assertThat(first.payloadJson()).contains("9007199254740993123456789", "1.2345678901234567890123456789");
        assertThat(first.payloadHash()).hasSize(64);
        assertThat(first.toString()).doesNotContain(first.payloadJson());
    }

    @Test
    void hashBindsEveryImmutableEnvelopeFieldAndRejectsMismatches() {
        String id = UUID.randomUUID().toString();
        var original = DurableMessage.create("upms", "workflow", id, "StartRequested", 1, "{\"v\":1}");
        assertThatThrownBy(() -> new DurableMessage("other", "workflow", id, "StartRequested", 1, original.payloadJson(), original.payloadHash())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurableMessage("upms", "other", id, "StartRequested", 1, original.payloadJson(), original.payloadHash())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurableMessage("upms", "workflow", id, "Other", 1, original.payloadJson(), original.payloadHash())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurableMessage("upms", "workflow", id, "StartRequested", 2, original.payloadJson(), original.payloadHash())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DurableMessage("upms", "workflow", id, "StartRequested", 1, "{\"v\":2}", original.payloadHash())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ambiguousJsonAndInvalidIdentifiersAreRejected() {
        String id = UUID.randomUUID().toString();
        for (String json : new String[]{"{\"v\":1,\"v\":2}", "{} {}", "NaN", ""}) {
            assertThatThrownBy(() -> DurableMessage.create("upms", "workflow", id, "StartRequested", 1, json)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> DurableMessage.create("upms", "workflow", "1-1-1-1-1", "StartRequested", 1, "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurableMessage.create("UPMS", "workflow", id, "StartRequested", 1, "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurableMessage.create("upms", "workflow", id, "StartRequested", 0, "{}")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void invalidUnicodeCannotCollapseDistinctPayloadsWhenHashingUtf8() {
        String id = UUID.randomUUID().toString();
        for (char unpaired : new char[] {(char) 0xd800, (char) 0xd801, (char) 0xdc00}) {
            String value = "\"" + unpaired + "\"";
            assertThatThrownBy(() -> DurableMessage.create("upms", "workflow", id, "StartRequested", 1, value))
                    .isInstanceOf(IllegalArgumentException.class);
            String key = "{" + value + ":1}";
            assertThatThrownBy(() -> DurableMessage.create("upms", "workflow", id, "StartRequested", 1, key))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        String emoji = new String(Character.toChars(0x1f680));
        var valid = DurableMessage.create("upms", "workflow", id, "StartRequested", 1, "\"" + emoji + "\"");
        assertThat(valid.payloadJson()).contains(emoji);
    }

}
