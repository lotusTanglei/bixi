package com.lotus.bixi.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.config.JacksonConfiguration;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class WorkflowStartJsonContractTest {
    private ObjectMapper applicationJson() {
        var builder = Jackson2ObjectMapperBuilder.json();
        new JacksonConfiguration().customizer().customize(builder);
        return builder.build();
    }
    @Test void longVariablesStayNumbersThroughTheActualApplicationMapper() throws Exception {
        var json = applicationJson();
        var dto = new ProcessStartDTO(); dto.setProcessKey("approval");
        dto.setVariables(Map.of("amount", 1L, "nested", List.of(Map.of("large", 9007199254740993L))));
        var wire = json.readTree(json.writeValueAsString(dto)).path("variables");
        assertThat(wire.path("amount").isNumber()).isTrue();
        assertThat(wire.path("nested").get(0).path("large").isNumber()).isTrue();
        assertThat(wire.path("nested").get(0).path("large").bigIntegerValue().toString()).isEqualTo("9007199254740993");
        var decoded = json.readValue(json.writeValueAsString(dto), ProcessStartDTO.class);
        assertThat(decoded.getVariables().get("amount")).isInstanceOf(Number.class);
    }
    @Test void httpDecimalsKeepTheirExactContentBeforeHashing() throws Exception {
        var dto = applicationJson().readValue("{\"processKey\":\"approval\",\"variables\":{\"precise\":0.12345678901234567890123456789,\"nested\":[{\"value\":1.0000000000000000000000000001}]}}", ProcessStartDTO.class);
        assertThat(dto.getVariables().get("precise")).isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
        assertThat(((Map<?, ?>) ((List<?>) dto.getVariables().get("nested")).get(0)).get("value"))
                .isEqualTo(new BigDecimal("1.0000000000000000000000000001"));
    }
    @Test void taskCompletionVariablesUseTheSameExactJsonContract() throws Exception {
        var json = applicationJson();
        var dto = json.readValue("{\"taskId\":\"task-1\",\"variables\":{\"large\":9007199254740993,\"precise\":0.12345678901234567890123456789}}",
                TaskCompleteDTO.class);
        assertThat(dto.getVariables().get("large").toString()).isEqualTo("9007199254740993");
        assertThat(dto.getVariables().get("precise"))
                .isEqualTo(new BigDecimal("0.12345678901234567890123456789"));
        String encoded = json.writeValueAsString(dto);
        var wire = json.readTree(encoded).path("variables");
        assertThat(wire.path("large").isNumber()).isTrue();
        assertThat(encoded).contains("\"precise\":0.12345678901234567890123456789");
    }
    @Test void localNonJsonValuesCannotBeConvertedIntoDifferentRemoteIntent() {
        var dto = new ProcessStartDTO(); dto.setVariables(Map.of("value", Double.NaN));
        assertThatThrownBy(() -> applicationJson().writeValueAsString(dto)).isInstanceOf(Exception.class);
    }
    @Test void workflowVariablesRejectIsolatedSurrogatesAtTheApplicationJsonBoundary() throws Exception {
        var json = applicationJson();
        for (String invalid : List.of("{\"\\uD800\":1}", "{\"text\":\"\\uD801\"}",
                "{\"nested\":[\"\\uDC00\"]}")) {
            String body = "{\"taskId\":\"task-1\",\"variables\":" + invalid + "}";
            assertThatThrownBy(() -> json.readValue(body, TaskCompleteDTO.class)).isInstanceOf(Exception.class);
        }
        var local = new TaskCompleteDTO();
        local.setVariables(Map.of("nested", List.of(Map.of("\uD800", "value"))));
        assertThatThrownBy(() -> json.writeValueAsString(local)).isInstanceOf(Exception.class);
        assertThat(json.readValue("{\"variables\":{\"\\uD83D\\uDE00\":\"\\uD83D\\uDE00\"}}",
                TaskCompleteDTO.class).getVariables()).containsEntry("\uD83D\uDE00", "\uD83D\uDE00");
    }
}
