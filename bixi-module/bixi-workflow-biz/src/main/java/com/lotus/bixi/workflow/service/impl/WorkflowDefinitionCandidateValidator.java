package com.lotus.bixi.workflow.service.impl;

import com.lotus.bixi.common.security.service.BixiUser;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.event.WorkflowTaskNotification;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.ScriptTask;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.io.InputStreamProvider;
import org.flowable.validation.ProcessValidatorFactory;
import org.flowable.validation.ValidationError;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Validates executable candidate metadata before Flowable persists a deployment. */
@Service
@ConditionalOnWorkflowEnabled
public class WorkflowDefinitionCandidateValidator {

    private static final Pattern SAFE_EXPRESSION = Pattern.compile(
            "^\\$\\{\\s*[A-Za-z_][A-Za-z0-9_]*(?:\\s*(?:==|!=|>=|<=|>|<)\\s*"
                    + "(?:true|false|null|-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?|'[^'\\\\\\r\\n]*'|\\\"[^\\\"\\\\\\r\\n]*\\\"))?\\s*}$");

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
            ValidatedModel validated = parse(input.readAllBytes(), location);
            validateCandidates(validated.model(), validated.tenantId());
            return validated.tenantId();
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
        ValidatedModel validated = parse(bytes, resourceName);
        validateCandidates(validated.model(), validated.tenantId());
        return validated.tenantId();
    }

    public ValidatedUpload validateUpload(byte[] bytes, String resourceName) {
        if (bytes == null || bytes.length == 0) {
            invalid("BPMN资源为空: " + resourceName);
        }
        ValidatedModel validated = parse(bytes, resourceName);
        List<Process> executable = validated.model().getProcesses().stream()
                .filter(Objects::nonNull)
                .filter(Process::isExecutable)
                .toList();
        if (executable.size() != 1) {
            invalid("上传文件只能包含一个可执行流程: " + resourceName);
        }
        Process process = executable.get(0);
        if (!WorkflowTaskNotification.isValidProcessKey(process.getId())) {
            invalid("可执行流程ID无效: " + resourceName);
        }
        rejectExecutableContent(process);
        rejectUnsafeXmlExpressions(bytes, resourceName);
        List<ValidationError> errors = new ProcessValidatorFactory().createDefaultProcessValidator()
                .validate(validated.model()).stream().filter(error -> !error.isWarning()).toList();
        if (!errors.isEmpty()) {
            String detail = errors.get(0).getDefaultDescription();
            if (detail == null || detail.isBlank()) detail = errors.get(0).getProblem();
            invalid("BPMN模型校验失败: " + (detail == null ? resourceName : detail));
        }
        validateCandidates(validated.model(), validated.tenantId());
        return new ValidatedUpload(validated.tenantId(), process.getId());
    }

    private ValidatedModel parse(byte[] bytes, String resourceName) {
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
            return new ValidatedModel(tenantId, model);
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

    private void validateCandidates(BpmnModel model, Long tenantId) {
        for (Process process : model.getProcesses()) {
            if (process == null) {
                continue;
            }
            for (UserTask task : process.findFlowElementsOfType(UserTask.class, true)) {
                candidates.validate(task, tenantId);
            }
        }
    }

    private static void rejectUnsafeXmlExpressions(byte[] bytes, String resourceName) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = null;
        Deque<ExpressionText> textByElement = new ArrayDeque<>();
        try {
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String id = reader.getAttributeValue(null, "id");
                    String element = hasText(id) ? id : reader.getLocalName();
                    for (int index = 0; index < reader.getAttributeCount(); index++) {
                        rejectUnsafeExpression(reader.getAttributeValue(index), element);
                    }
                    textByElement.push(new ExpressionText(element, new StringBuilder()));
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA)
                        && !textByElement.isEmpty()) {
                    textByElement.peek().value().append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && !textByElement.isEmpty()) {
                    ExpressionText text = textByElement.pop();
                    rejectUnsafeExpression(text.value().toString(), text.element());
                }
            }
        } catch (XMLStreamException ex) {
            invalid("BPMN表达式校验失败: " + resourceName);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException ignored) {
                    // The upload bytes have already been consumed; there is no external resource to release.
                }
            }
        }
    }

    private static void rejectExecutableContent(Process process) {
        if (!process.findFlowElementsOfType(ScriptTask.class, true).isEmpty()) {
            invalid("上传流程不允许脚本任务");
        }
        for (ServiceTask task : process.findFlowElementsOfType(ServiceTask.class, true)) {
            if (hasText(task.getImplementation()) || hasText(task.getImplementationType())
                    || hasText(task.getType()) || hasText(task.getSkipExpression())) {
                invalid("上传流程不允许自定义执行实现: " + task.getId());
            }
        }
        for (FlowElement element : process.findFlowElementsOfType(FlowElement.class, true)) {
            if (element.getExecutionListeners() != null && !element.getExecutionListeners().isEmpty()) {
                invalid("上传流程不允许执行监听器: " + element.getId());
            }
            if (element instanceof UserTask task && task.getTaskListeners() != null
                    && !task.getTaskListeners().isEmpty()) {
                invalid("上传流程不允许任务监听器: " + task.getId());
            }
            if (element instanceof UserTask task) {
                rejectUnsafeExpression(task.getAssignee(), task.getId());
                rejectUnsafeExpression(task.getOwner(), task.getId());
                rejectUnsafeExpression(task.getPriority(), task.getId());
                rejectUnsafeExpression(task.getFormKey(), task.getId());
                rejectUnsafeExpression(task.getDueDate(), task.getId());
                rejectUnsafeExpression(task.getCategory(), task.getId());
                rejectUnsafeExpression(task.getSkipExpression(), task.getId());
            }
            if (element instanceof SequenceFlow flow) {
                rejectUnsafeExpression(flow.getConditionExpression(), flow.getId());
                rejectUnsafeExpression(flow.getSkipExpression(), flow.getId());
            }
        }
        if (process.getExecutionListeners() != null && !process.getExecutionListeners().isEmpty()) {
            invalid("上传流程不允许流程监听器: " + process.getId());
        }
    }

    private static void rejectUnsafeExpression(String value, String elementId) {
        if (!hasText(value)) {
            return;
        }
        String expression = value.trim();
        if ((expression.contains("${") || expression.contains("#{"))
                && !SAFE_EXPRESSION.matcher(expression).matches()) {
            invalid("上传流程包含不安全表达式: " + elementId);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void invalid(String detail) {
        throw new IllegalArgumentException(WorkflowCandidateResolver.INVALID_PREFIX + detail);
    }

    public record ValidatedUpload(Long tenantId, String processKey) {
    }

    private record ExpressionText(String element, StringBuilder value) {
    }

    private record ValidatedModel(Long tenantId, BpmnModel model) {
    }
}
