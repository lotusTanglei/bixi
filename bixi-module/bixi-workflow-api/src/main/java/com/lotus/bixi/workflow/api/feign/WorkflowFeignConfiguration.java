package com.lotus.bixi.workflow.api.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.workflow.api.dto.WorkflowRequestDTO;
import com.lotus.bixi.workflow.api.exception.WorkflowCommandNotFoundException;
import com.lotus.bixi.workflow.api.exception.WorkflowOperationConflictException;
import com.lotus.bixi.workflow.api.exception.WorkflowRequestConflictException;

import feign.Feign;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.security.access.AccessDeniedException;

/** Client-only configuration; intentionally not component scanned into other Feign clients. */
public class WorkflowFeignConfiguration {

    @Bean
    @Scope("prototype")
    public Feign.Builder workflowFeignBuilder() {
        // Workflow authorization and service failures must not become Sentinel's automatic R.failed fallback.
        return Feign.builder();
    }

    @Bean
    public ErrorDecoder workflowErrorDecoder() {
        ErrorDecoder defaultDecoder = new ErrorDecoder.Default();
        ObjectMapper json = new ObjectMapper();
        return (methodKey, response) -> {
            if ((response.status() == 409 || response.status() == 404) && response.body() != null) {
                try (var stream = response.body().asInputStream()) {
                    var data = json.readTree(stream).path("data");
                    String requestId = data.path("requestId").asText();
                    if (requestId.matches(WorkflowRequestDTO.REQUEST_ID_PATTERN)) {
                        if (response.status() == 409 && "WORKFLOW_REQUEST_CONFLICT".equals(data.path("errorCode").asText())) {
                            return new WorkflowRequestConflictException(requestId);
                        }
                        if (response.status() == 409 && "WORKFLOW_OPERATION_CONFLICT".equals(data.path("errorCode").asText())) {
                            return new WorkflowOperationConflictException(requestId);
                        }
                        if (response.status() == 404 && "WORKFLOW_COMMAND_NOT_FOUND".equals(data.path("errorCode").asText())) {
                            return new WorkflowCommandNotFoundException(requestId);
                        }
                    }
                } catch (java.io.IOException | RuntimeException ignored) {
                    // Unknown/malformed error envelopes retain the existing sanitized client-error contract.
                }
            }
            return switch (response.status()) {
                case 401, 403 -> new AccessDeniedException("Access is denied");
                case 400, 404, 409, 422 -> new IllegalArgumentException("Workflow request could not be completed");
                default -> defaultDecoder.decode(methodKey, response);
            };
        };
    }
}
