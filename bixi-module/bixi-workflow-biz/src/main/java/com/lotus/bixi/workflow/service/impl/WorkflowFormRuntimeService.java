package com.lotus.bixi.workflow.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lotus.bixi.common.security.util.SecurityUtils;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.entity.SysFormPermission;
import com.lotus.bixi.workflow.api.entity.SysRoleFormPermission;
import com.lotus.bixi.workflow.api.entity.WfForm;
import com.lotus.bixi.workflow.api.entity.WfFormData;
import com.lotus.bixi.workflow.api.entity.WfFormVersion;
import com.lotus.bixi.workflow.api.entity.WfProcessDefinition;
import com.lotus.bixi.workflow.api.entity.WfProcessInstance;
import com.lotus.bixi.workflow.api.vo.FormRenderVO;
import com.lotus.bixi.workflow.command.WorkflowRequestHasher;
import com.lotus.bixi.workflow.mapper.SysFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.SysRoleFormPermissionMapper;
import com.lotus.bixi.workflow.mapper.WfFormDataMapper;
import com.lotus.bixi.workflow.mapper.WfFormMapper;
import com.lotus.bixi.workflow.mapper.WfFormVersionMapper;
import com.lotus.bixi.workflow.mapper.WfProcessDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns the immutable form binding and field authorization used by workflow mutations. */
@Service
@ConditionalOnWorkflowEnabled
@RequiredArgsConstructor
public class WorkflowFormRuntimeService {

    public static final String START_TASK_KEY = "__start__";

    private final WfProcessDefinitionMapper definitions;
    private final WfFormMapper forms;
    private final WfFormVersionMapper versions;
    private final WfFormDataMapper data;
    private final SysFormPermissionMapper permissions;
    private final SysRoleFormPermissionMapper rolePermissions;
    private final WorkflowFormSchema schemas;
    private final WorkflowRequestHasher hasher;

    public PreparedForm prepareStart(String processDefinitionId, Long assertedFormId, String dataJson) {
        WfProcessDefinition definition = definitions.selectOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId));
        if (definition == null || definition.getFormVersionId() == null) {
            if (assertedFormId != null || dataJson != null && !dataJson.isBlank()) {
                throw new IllegalArgumentException("流程定义未绑定表单");
            }
            return null;
        }
        WfFormVersion version = requireVersion(definition.getFormVersionId());
        if (assertedFormId != null && !assertedFormId.equals(version.getFormId())) {
            throw new IllegalArgumentException("请求表单与流程定义绑定表单不一致");
        }
        return prepare(version, processDefinitionId, START_TASK_KEY, null, dataJson);
    }

    public FormRenderVO renderStart(String processDefinitionId) {
        WfProcessDefinition definition = definitions.selectOne(Wrappers.<WfProcessDefinition>lambdaQuery()
                .eq(WfProcessDefinition::getProcessDefinitionId, processDefinitionId));
        if (definition == null) {
            throw new IllegalArgumentException("流程定义不存在");
        }
        if (definition.getFormVersionId() == null) {
            return null;
        }
        WfFormVersion version = requireVersion(definition.getFormVersionId());
        return renderVersion(version, processDefinitionId, START_TASK_KEY,
                JsonNodeFactory.instance.objectNode());
    }

    public PreparedForm prepareTask(WfProcessInstance instance, String taskDefinitionKey, String submittedJson) {
        return prepareTask(instance, instance == null ? null : instance.getProcessDefinitionId(),
                taskDefinitionKey, null, submittedJson);
    }

    public PreparedForm prepareTask(WfProcessInstance instance, String processDefinitionId,
            String taskDefinitionKey, Long assertedFormId, String submittedJson) {
        requireInstance(instance);
        if (!Objects.equals(instance.getProcessDefinitionId(), processDefinitionId)) {
            throw new IllegalStateException("流程实例与当前任务定义不一致");
        }
        if (instance.getFormVersionId() == null || instance.getFormId() == null) {
            if (assertedFormId != null || submittedJson != null && !submittedJson.isBlank()) {
                throw new IllegalArgumentException("流程实例未绑定表单");
            }
            return null;
        }
        if (assertedFormId != null && !instance.getFormId().equals(assertedFormId)) {
            throw new IllegalArgumentException("请求表单与流程实例绑定表单不一致");
        }
        WfFormVersion version = requireVersion(instance.getFormVersionId());
        if (!instance.getFormId().equals(version.getFormId())) {
            throw new IllegalStateException("流程实例表单绑定无效");
        }
        WfFormData previous = latestData(instance.getProcessInstanceId());
        return prepare(version, processDefinitionId, taskDefinitionKey, previous, submittedJson);
    }

    public FormRenderVO render(WfProcessInstance instance, String taskDefinitionKey) {
        return render(instance, instance == null ? null : instance.getProcessDefinitionId(), taskDefinitionKey);
    }

    public FormRenderVO render(WfProcessInstance instance, String processDefinitionId, String taskDefinitionKey) {
        requireInstance(instance);
        if (!Objects.equals(instance.getProcessDefinitionId(), processDefinitionId)) {
            throw new IllegalStateException("流程实例与当前任务定义不一致");
        }
        if (instance.getFormVersionId() == null || instance.getFormId() == null) {
            return null;
        }
        WfFormVersion version = requireVersion(instance.getFormVersionId());
        if (!instance.getFormId().equals(version.getFormId())) {
            throw new IllegalStateException("流程实例表单绑定无效");
        }
        WfFormData snapshot = latestData(instance.getProcessInstanceId());
        ObjectNode formData = snapshot == null
                ? JsonNodeFactory.instance.objectNode()
                : requireObject(hasher.parse(snapshot.getFormDataJson()), "历史表单数据").deepCopy();
        return renderVersion(version, processDefinitionId, taskDefinitionKey, formData);
    }

    private FormRenderVO renderVersion(WfFormVersion version, String processDefinitionId,
            String taskDefinitionKey, ObjectNode formData) {
        WorkflowFormSchema.Compiled compiled = schemas.compile(version.getSchemaJson());
        Map<String, String> access = fieldAccess(version.getFormId(), version.getId(),
                processDefinitionId, taskDefinitionKey, compiled);
        Set<String> hidden = new HashSet<>();
        access.forEach((field, permission) -> {
            if ("hidden".equals(permission)) hidden.add(field);
        });

        JsonNode filteredSchema = filterSchema(compiled.source(), hidden);
        hidden.forEach(formData::remove);

        WfForm form = forms.selectById(version.getFormId());
        if (form == null) {
            throw new IllegalStateException("绑定表单不存在");
        }
        FormRenderVO render = new FormRenderVO();
        render.setFormId(form.getId());
        render.setFormVersionId(version.getId());
        render.setFormKey(form.getFormKey());
        render.setFormName(form.getFormName());
        render.setVersion(version.getVersion());
        render.setSchemaJson(hasher.canonical(filteredSchema));
        render.setDataJson(hasher.canonical(formData));
        render.setPermissions(access);
        return render;
    }

    private PreparedForm prepare(WfFormVersion version, String processDefinitionId, String taskDefinitionKey,
            WfFormData previous, String submittedJson) {
        WorkflowFormSchema.Compiled compiled = schemas.compile(version.getSchemaJson());
        Map<String, String> access = fieldAccess(version.getFormId(), version.getId(),
                processDefinitionId, taskDefinitionKey, compiled);
        ObjectNode submitted = submittedJson == null || submittedJson.isBlank()
                ? JsonNodeFactory.instance.objectNode()
                : requireObject(hasher.parse(submittedJson), "表单数据");
        submitted.fieldNames().forEachRemaining(field -> {
            if (!compiled.fields().containsKey(field)) {
                throw new IllegalArgumentException("未知字段: " + field);
            }
            if (!"edit".equals(access.get(field))) {
                throw new IllegalArgumentException("字段 " + field + " 不可修改");
            }
        });

        ObjectNode merged = previous == null || previous.getFormDataJson() == null
                ? JsonNodeFactory.instance.objectNode()
                : requireObject(hasher.parse(previous.getFormDataJson()), "历史表单数据").deepCopy();
        submitted.fields().forEachRemaining(field -> merged.set(field.getKey(), field.getValue()));
        ObjectNode validated = schemas.validate(compiled, hasher.canonical(merged));
        return new PreparedForm(version.getFormId(), version.getId(), previous == null ? null : previous.getId(),
                hasher.canonical(validated));
    }

    private Map<String, String> fieldAccess(Long formId, Long formVersionId, String processDefinitionId,
            String taskDefinitionKey, WorkflowFormSchema.Compiled compiled) {
        List<SysFormPermission> scoped = permissions.selectList(Wrappers.<SysFormPermission>lambdaQuery()
                .eq(SysFormPermission::getFormId, formId)
                .and(query -> query.isNull(SysFormPermission::getFormVersionId)
                        .or().eq(SysFormPermission::getFormVersionId, formVersionId))
                .and(query -> query.isNull(SysFormPermission::getProcessDefinitionId)
                        .or().eq(SysFormPermission::getProcessDefinitionId, processDefinitionId))
                .and(query -> query.isNull(SysFormPermission::getTaskDefinitionKey)
                        .or().eq(SysFormPermission::getTaskDefinitionKey, taskDefinitionKey)));

        Set<Long> roleIds = currentRoleIds();
        Set<Long> granted = new HashSet<>();
        if (!roleIds.isEmpty()) {
            rolePermissions.selectList(Wrappers.<SysRoleFormPermission>lambdaQuery()
                            .in(SysRoleFormPermission::getRoleId, roleIds))
                    .stream().map(SysRoleFormPermission::getFormPermId).forEach(granted::add);
        }

        Map<String, List<SysFormPermission>> byField = new HashMap<>();
        for (SysFormPermission permission : scoped) {
            if (permission.getFieldCode() != null && compiled.fields().containsKey(permission.getFieldCode())) {
                byField.computeIfAbsent(permission.getFieldCode(), ignored -> new ArrayList<>()).add(permission);
            }
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : compiled.fields().keySet()) {
            List<SysFormPermission> rules = byField.get(field);
            if (rules == null || rules.isEmpty()) {
                result.put(field, "edit");
                continue;
            }
            String effective = rules.stream().filter(rule -> granted.contains(rule.getId()))
                    .map(rule -> normalizePermission(rule.getPermType()))
                    .max(java.util.Comparator.comparingInt(WorkflowFormRuntimeService::permissionRank))
                    .orElse("hidden");
            result.put(field, effective);
        }
        return result;
    }

    private Set<Long> currentRoleIds() {
        if (SecurityUtils.getAuthentication() == null) return Set.of();
        Set<Long> roles = new HashSet<>();
        for (GrantedAuthority authority : SecurityUtils.getAuthentication().getAuthorities()) {
            String value = authority.getAuthority();
            if (value != null && value.matches("ROLE_[1-9][0-9]*")) {
                roles.add(Long.parseLong(value.substring("ROLE_".length())));
            }
        }
        return roles;
    }

    private WfFormVersion requireVersion(Long id) {
        WfFormVersion version = versions.selectById(id);
        if (version == null) throw new IllegalStateException("绑定表单版本不存在");
        return version;
    }

    private WfFormData latestData(String processInstanceId) {
        return data.selectOne(Wrappers.<WfFormData>lambdaQuery()
                .eq(WfFormData::getProcessInstanceId, processInstanceId)
                .orderByDesc(WfFormData::getCreateTime)
                .orderByDesc(WfFormData::getId)
                .last("LIMIT 1"));
    }

    private JsonNode filterSchema(JsonNode node, Set<String> hidden) {
        if (node.isObject()) {
            JsonNode options = node.get("options");
            if (options != null && options.isObject() && options.path("name").isTextual()
                    && hidden.contains(options.path("name").textValue())) {
                return null;
            }
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(field -> {
                JsonNode filtered = filterSchema(field.getValue(), hidden);
                if (filtered != null) result.set(field.getKey(), filtered);
            });
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = JsonNodeFactory.instance.arrayNode();
            node.forEach(child -> {
                JsonNode filtered = filterSchema(child, hidden);
                if (filtered != null) result.add(filtered);
            });
            return result;
        }
        return node.deepCopy();
    }

    private static ObjectNode requireObject(JsonNode value, String label) {
        if (!(value instanceof ObjectNode object)) {
            throw new IllegalArgumentException(label + "必须是 JSON 对象");
        }
        return object;
    }

    private static String normalizePermission(String permission) {
        if (permission == null) return "hidden";
        return switch (permission.toLowerCase(java.util.Locale.ROOT)) {
            case "edit", "write" -> "edit";
            case "readonly", "read", "view" -> "readonly";
            case "hidden", "hide" -> "hidden";
            default -> "hidden";
        };
    }

    private static int permissionRank(String permission) {
        return switch (permission) {
            case "edit" -> 3;
            case "readonly" -> 2;
            default -> 1;
        };
    }

    private static void requireInstance(WfProcessInstance instance) {
        if (instance == null || instance.getProcessInstanceId() == null || instance.getProcessDefinitionId() == null) {
            throw new IllegalArgumentException("流程实例无效");
        }
    }

    public record PreparedForm(Long formId, Long formVersionId, Long dataId, String dataJson) {
    }
}
