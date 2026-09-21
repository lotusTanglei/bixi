package com.lotus.bixi.workflow.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.feign.sentinel.SentinelAutoConfiguration;
import com.lotus.bixi.workflow.api.dto.ProcessStartDTO;
import com.lotus.bixi.workflow.api.dto.TaskCompleteDTO;
import com.lotus.bixi.workflow.api.dto.TaskRejectDTO;
import com.lotus.bixi.workflow.api.dto.TaskTransferDTO;
import com.lotus.bixi.workflow.api.feign.RemoteWorkflowService;
import com.lotus.bixi.workflow.api.service.WorkflowService;
import com.sun.net.httpserver.HttpServer;
import feign.FeignException;
import feign.RetryableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Exercise Spring MVC's Feign contract and JSON conversion over a real test-only HTTP connection. */
class WorkflowRemoteContractTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final AtomicReference<CapturedRequest> request = new AtomicReference<>();
    private volatile String response = "{\"code\":0,\"data\":{\"processInstanceId\":\"process-123\"}}";
    private volatile int responseStatus = 200;
    private HttpServer server;
    private AnnotationConfigApplicationContext context;
    private WorkflowService remote;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            request.set(new CapturedRequest(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(responseStatus, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        startClient(false);
    }

    @AfterEach
    void stopServer() {
        if (context != null) {
            context.close();
        }
        server.stop(0);
    }

    private void startClient(boolean sentinelEnabled) {
        if (context != null) {
            context.close();
        }
        context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "workflow.enabled", "true", "bixi.deployment.mode", "cloud",
                "spring.cloud.openfeign.sentinel.enabled", Boolean.toString(sentinelEnabled),
                "spring.cloud.openfeign.client.config.remoteWorkflowService.url",
                "http://127.0.0.1:" + server.getAddress().getPort())));
        context.registerBean(ObjectMapper.class, () -> mapper);
        context.registerBean(HttpMessageConverters.class,
                () -> new HttpMessageConverters(new MappingJackson2HttpMessageConverter(mapper)));
        context.register(Clients.class, FeignAutoConfiguration.class, SentinelAutoConfiguration.class);
        context.refresh();
        remote = context.getBean(WorkflowService.class);
    }

    @Test
    void detailsUseThePublishedControllerRouteAndDecodeTheResult() {
        var result = remote.getProcessInstance("process-123");

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().getProcessInstanceId()).isEqualTo("process-123");
        assertThat(request.get().path()).isEqualTo("/workflow/process/details/process-123");
        assertThat(request.get().method()).isEqualTo("GET");
    }

    @Test
    void startEncodesTheRequestDtoAndDecodesTheInstance() throws Exception {
        ProcessStartDTO dto = new ProcessStartDTO();
        dto.setRequestId(java.util.UUID.randomUUID().toString());
        dto.setProcessKey("approval");
        dto.setBusinessKey("order-42");
        dto.setVariables(Map.of("approver", "7"));

        var result = remote.startProcess(dto);

        assertThat(result.getData().getProcessInstanceId()).isEqualTo("process-123");
        assertJsonPost("/workflow/process/start", dto);
    }

    @Test
    void commandLookupUsesActorScopedPublishedRoute() {
        String id = "12345678-1234-1234-1234-123456789abc";
        response = "{\"code\":0,\"data\":{\"requestId\":\"" + id
                + "\",\"operation\":\"START\",\"resultCode\":\"SUCCESS\",\"response\":{\"processInstanceId\":\"process-123\"}}}";
        var command = remote.getCommand(id).getData();
        assertThat(command.requestId()).isEqualTo(id);
        assertThat(command.response().get("processInstanceId").textValue()).isEqualTo("process-123");
        assertThat(request.get().path()).isEqualTo("/workflow/command/" + id);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void idempotencyConflictRetainsItsMeaningAcrossFeign(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        String id = "12345678-1234-1234-1234-123456789abc";
        responseStatus = 409;
        response = "{\"code\":1,\"msg\":\"private diagnostic\",\"data\":{\"errorCode\":\"WORKFLOW_REQUEST_CONFLICT\",\"requestId\":\"" + id + "\"}}";
        assertThatThrownBy(() -> remote.startProcess(new ProcessStartDTO()))
                .isInstanceOfSatisfying(com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException.class,
                        failure -> assertThat(failure.getRequestId()).isEqualTo(id));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void operationConflictRetainsItsMeaningAcrossFeign(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        String id = "12345678-1234-1234-1234-123456789abc";
        responseStatus = 409;
        response = "{\"code\":1,\"msg\":\"private diagnostic\",\"data\":{\"errorCode\":\"WORKFLOW_OPERATION_CONFLICT\",\"requestId\":\"" + id + "\"}}";
        assertThatThrownBy(() -> remote.completeTask(new TaskCompleteDTO()))
                .isInstanceOfSatisfying(com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException.class,
                        failure -> assertThat(failure.getRequestId()).isEqualTo(id));
    }

    @Test
    void taskWritesUseVoidResponsesAndEncodeTheirDistinctDtos() throws Exception {
        response = "{\"code\":0,\"data\":null}";
        TaskCompleteDTO complete = new TaskCompleteDTO();
        complete.setTaskId("task-1");
        complete.setApprovalComment("同意");
        complete.setVariables(Map.of("approved", true));
        assertThat(remote.completeTask(complete).getData()).isNull();
        assertJsonPost("/workflow/task/complete", complete);

        TaskRejectDTO reject = new TaskRejectDTO();
        reject.setTaskId("task-2");
        reject.setRejectReason("资料不完整");
        assertThat(remote.rejectTask(reject).getCode()).isZero();
        assertJsonPost("/workflow/task/reject", reject);

        TaskTransferDTO transfer = new TaskTransferDTO();
        transfer.setTaskId("task-3");
        transfer.setTransferUserId(9L);
        transfer.setTransferReason("交接");
        assertThat(remote.transferTask(transfer).getData()).isNull();
        assertJsonPost("/workflow/task/transfer", transfer);
    }

    @Test
    void todoAndDoneUsePaginationWithoutCallerSelectedIdentity() {
        response = "{\"code\":0,\"data\":{\"current\":2,\"size\":5,\"total\":13,"
                + "\"records\":[{\"taskId\":\"task-7\",\"assignee\":\"7\"}]}}";
        var todo = remote.getTodoTasks(2, 5).getData();
        assertThat(request.get().path()).isEqualTo("/workflow/task/todo/page?current=2&size=5");
        assertThat(todo.getCurrent()).isEqualTo(2);
        assertThat(todo.getSize()).isEqualTo(5);
        assertThat(todo.getTotal()).isEqualTo(13);
        assertThat(todo.getRecords()).singleElement().satisfies(task -> {
            assertThat(task.getTaskId()).isEqualTo("task-7");
            assertThat(task.getAssignee()).isEqualTo("7");
        });

        var done = remote.getDoneTasks(2, 5).getData();
        assertThat(request.get().path()).isEqualTo("/workflow/task/done/page?current=2&size=5");
        assertThat(done.getRecords()).extracting("taskId").containsExactly("task-7");
        assertThat(request.get().method()).isEqualTo("GET");
    }

    @Test
    void historyUsesThePublishedRouteAndDecodesApprovalRecords() {
        response = "{\"code\":0,\"data\":[{\"taskId\":\"task-7\",\"approvalType\":\"approve\","
                + "\"approvalUserId\":7,\"approvalComment\":\"同意\"}]}";
        var result = remote.getApprovalHistory("process-123");

        assertThat(request.get().path()).isEqualTo("/workflow/process/history/process-123");
        assertThat(result.getData()).singleElement().satisfies(record -> {
            assertThat(record.getTaskId()).isEqualTo("task-7");
            assertThat(record.getApprovalType()).isEqualTo("approve");
            assertThat(record.getApprovalUserId()).isEqualTo(7L);
            assertThat(record.getApprovalComment()).isEqualTo("同意");
        });
    }

    @Test
    void discoveryUsesTheWorkflowApplicationName() {
        assertThat(RemoteWorkflowService.class.getAnnotation(FeignClient.class).value())
                .isEqualTo("bixi-workflow-biz");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authorizationFailuresRemainAccessDeniedWithoutRemoteDetails(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        response = "{\"code\":1,\"msg\":\"internal http://workflow/private diagnostic\"}";
        for (int status : new int[] {401, 403}) {
            responseStatus = status;
            assertThatThrownBy(() -> remote.getProcessInstance("process-123"))
                    .isInstanceOf(AccessDeniedException.class)
                    .hasMessage("Access is denied");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitClientErrorsRemainBusinessFailuresWithoutRemoteDetails(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        response = "{\"code\":1,\"msg\":\"internal http://workflow/private diagnostic\"}";
        for (int status : new int[] {400, 404, 409, 422}) {
            responseStatus = status;
            assertThatThrownBy(() -> remote.getProcessInstance("process-123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Workflow request could not be completed");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void serviceFaultsAndRateLimitsRemainTransportFailures(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        response = "{\"code\":1,\"msg\":\"unavailable\"}";
        for (int status : new int[] {429, 500, 503}) {
            responseStatus = status;
            assertThatThrownBy(() -> remote.getProcessInstance("process-123"))
                    .isInstanceOfSatisfying(FeignException.class, failure -> assertThat(failure.status()).isEqualTo(status));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void connectionFailuresRemainTransportFailures(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        server.stop(0);
        assertThatThrownBy(() -> remote.getProcessInstance("process-123"))
                .isInstanceOf(RetryableException.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void publishedBusinessFailureResponsesRemainIntact(boolean sentinelEnabled) {
        startClient(sentinelEnabled);
        response = "{\"code\":1,\"msg\":\"Process has already ended\",\"data\":null}";
        var result = remote.getProcessInstance("process-123");
        assertThat(result.getCode()).isEqualTo(1);
        assertThat(result.getMsg()).isEqualTo("Process has already ended");
        assertThat(result.getData()).isNull();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = RemoteWorkflowService.class)
    static class Clients {
    }

    private void assertJsonPost(String path, Object dto) throws Exception {
        assertThat(request.get().path()).isEqualTo(path);
        assertThat(request.get().method()).isEqualTo("POST");
        assertThat(mapper.readTree(request.get().body())).isEqualTo(mapper.readTree(mapper.writeValueAsString(dto)));
    }

    private record CapturedRequest(String method, String path, String body) {
    }
}
