package com.lotus.bixi.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.constant.SecurityConstants;
import com.lotus.bixi.common.feign.core.BixiFeignInnerRequestInterceptor;
import com.lotus.bixi.workflow.api.dto.WorkflowResultDTO;
import com.lotus.bixi.workflow.api.feign.RemoteWorkflowResultReceiver;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowResultContractTest {
    @Test void remoteResultUsesInternalHeaderAndPreservesIdentityOverActualHttp() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var captured = new AtomicReference<String>();
        var from = new AtomicReference<String>();
        var path = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            from.set(exchange.getRequestHeaders().getFirst(SecurityConstants.FROM));
            path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            byte[] bytes = "{\"code\":0,\"data\":null}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        try {
            var converters = new HttpMessageConverters(new MappingJackson2HttpMessageConverter(mapper));
            var remote = Feign.builder().contract(new SpringMvcContract())
                    .requestInterceptor(new BixiFeignInnerRequestInterceptor())
                    .encoder(new SpringEncoder(() -> converters)).decoder(new SpringDecoder(() -> converters))
                    .target(RemoteWorkflowResultReceiver.class, "http://127.0.0.1:" + server.getAddress().getPort());
            var result = new WorkflowResultDTO();
            result.setEventId("event-1"); result.setProcessInstanceId("process-1");
            result.setProcessKey("demo_leave_approval"); result.setBusinessTable("demo_leave_request");
            result.setBusinessKey("demo_leave:42:1"); result.setBusinessId(42L); result.setRound(1);
            result.setStartUserId(11L); result.setStatus("completed"); result.setEndTime(LocalDateTime.of(2026, 10, 1, 10, 0));
            assertThat(remote.receive(result).getCode()).isZero();
            assertThat(from.get()).isEqualTo(SecurityConstants.FROM_IN);
            assertThat(path.get()).isEqualTo("POST /demo/leave/internal/workflow-result");
            assertThat(mapper.readValue(captured.get(), WorkflowResultDTO.class)).isEqualTo(result);
        }
        finally { server.stop(0); }
    }
}
