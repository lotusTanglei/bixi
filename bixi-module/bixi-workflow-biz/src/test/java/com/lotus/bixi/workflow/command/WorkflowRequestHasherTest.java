package com.lotus.bixi.workflow.command;

import com.fasterxml.jackson.databind.node.DoubleNode;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class WorkflowRequestHasherTest {
    final WorkflowRequestHasher hasher = new WorkflowRequestHasher();
    final WorkflowRequestHasher.Actor actor = new WorkflowRequestHasher.Actor("default", 11L);

    @Test void nestedKeysNumbersAndUnicodeHaveOneCanonicalHash() {
        var a = hasher.normalize(Map.of("字", List.of(Map.of("b", new BigDecimal("1.00"), "a", -0.0)), "a", "申请"));
        var b = hasher.parse("{\"a\":\"申请\",\"字\":[{\"a\":0,\"b\":1e0}]}");
        assertThat(hasher.canonical(a)).isEqualTo("{\"a\":\"申请\",\"字\":[{\"a\":0,\"b\":1}]}");
        assertThat(hash(a)).hasSize(64).isEqualTo(hash(b));
    }
    @Test void arraysAndIdentityAndOperationRemainSignificant() {
        var payload = hasher.parse("{\"round\":1,\"values\":[1,2]}");
        assertThat(hash(payload)).isNotEqualTo(hash(hasher.parse("{\"round\":1,\"values\":[2,1]}")))
                .isNotEqualTo(hash(hasher.parse("{\"round\":2,\"values\":[1,2]}")))
                .isNotEqualTo(hasher.hash("COMPLETE", "approval", "public", actor, payload))
                .isNotEqualTo(hasher.hash("START", "other", "public", actor, payload))
                .isNotEqualTo(hasher.hash("START", "approval", "upms", actor, payload))
                .isNotEqualTo(hasher.hash("START", "approval", "public", new WorkflowRequestHasher.Actor("default", 12L), payload));
    }
    @Test void rejectsNonJsonAndAmbiguousValues() {
        for (Object invalid : List.of(new Object(), Double.NaN, Double.POSITIVE_INFINITY,
                DoubleNode.valueOf(Double.NEGATIVE_INFINITY), Map.of(1, "non-string key"))) {
            assertThatThrownBy(() -> hasher.normalize(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String invalid : List.of("NaN", "{} {}", "{\"a\":1,\"a\":2}", "1e999999999")) {
            assertThatThrownBy(() -> hasher.parse(invalid)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void rejectsUnpairedSurrogatesBeforeUtf8Hashing() {
        String highA = "\uD800";
        String highB = "\uD801";
        String low = "\uDC00";
        assertThatThrownBy(() -> hasher.normalize(Map.of("message", highA))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.normalize(Map.of(highB, "value"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.normalize(List.of(low))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse("{\"\\uD800\":\"text\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.parse("{\"text\":\"\\uD801\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash(highA, "task", "workflow", actor, hasher.parse("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash("COMMENT", highA, "workflow", actor, hasher.parse("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash("COMMENT", "task", low, actor, hasher.parse("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hasher.hash("COMMENT", "task", "workflow",
                new WorkflowRequestHasher.Actor(highB, 11L), hasher.parse("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        String pair = "\uD83D\uDE00";
        assertThat(hash(hasher.normalize(Map.of(pair, List.of(pair))))).hasSize(64);
        assertThat(hasher.parse("{\"\\uD83D\\uDE00\":\"\\uD83D\\uDE00\"}"))
                .isEqualTo(hasher.normalize(Map.of(pair, pair)));
    }
    private String hash(com.fasterxml.jackson.databind.JsonNode payload) {
        return hasher.hash("START", "approval", "public", actor, payload);
    }
}
