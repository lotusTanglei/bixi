package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.io.InputStreamProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Objects;

/** Validates executable candidate metadata before Flowable persists a deployment. */
@Service
@ConditionalOnWorkflowEnabled
public class WorkflowDefinitionCandidateValidator {

    private final ResourceLoader resources;
    private final WorkflowAccessService access;
    private final WorkflowCandidateResolver candidates;

    public WorkflowDefinitionCandidateValidator(ResourceLoader resources,
                                                WorkflowAccessService access,
                                                WorkflowCandidateResolver candidates) {
        this.resources = Objects.requireNonNull(resources, "resources");
        this.access = Objects.requireNonNull(access, "access");
        this.candidates = Objects.requireNonNull(candidates, "candidates");
    }

    public Long validateClasspathResource(String location) {
        Resource resource = resources.getResource(location.startsWith("classpath:") ? location : "classpath:" + location);
        if (resource == null || !resource.exists()) {
            invalid("BPMN资源不存在: " + location);
        }
        try (InputStream input = resource.getInputStream()) {
            return validate(input.readAllBytes(), location);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            invalid("BPMN资源读取失败: " + location);
        }
        return null;
    }

    public Long validateBytes(byte[] bytes, String resourceName) {
        if (bytes == null || bytes.length == 0) {
            invalid("BPMN资源为空: " + resourceName);
        }
        return validate(bytes, resourceName);
    }

    private Long validate(byte[] bytes, String resourceName) {
        BixiUser actor = access.currentUser();
        Long tenantId = actor.getTenantId();
        if (tenantId == null || tenantId <= 0) {
            invalid("当前用户租户无效");
        }
        try {
            InputStreamProvider provider = () -> new ByteArrayInputStream(bytes);
            BpmnModel model = new BpmnXMLConverter().convertToBpmnModel(provider, false, false);
            if (model == null || model.getProcesses() == null || model.getProcesses().isEmpty()) {
                invalid("BPMN模型为空: " + resourceName);
            }
            for (Process process : model.getProcesses()) {
                if (process == null) {
                    continue;
                }
                for (UserTask task : process.findFlowElementsOfType(UserTask.class, true)) {
                    candidates.validate(task, tenantId);
                }
            }
            return tenantId;
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith(WorkflowCandidateResolver.INVALID_PREFIX)) {
                throw ex;
            }
            invalid("BPMN解析失败: " + resourceName);
        } catch (Exception ex) {
            invalid("BPMN解析失败: " + resourceName);
        }
        return null;
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException(WorkflowCandidateResolver.INVALID_PREFIX + detail);
    }
}
