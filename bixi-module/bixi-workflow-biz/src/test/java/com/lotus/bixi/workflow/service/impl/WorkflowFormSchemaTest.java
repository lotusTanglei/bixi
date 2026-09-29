package com.lotus.bixi.workflow.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFormSchemaTest {

    private final WorkflowFormSchema schemas = new WorkflowFormSchema(new ObjectMapper());

    @Test
    void compilesSupportedFieldsAndValidatesCanonicalData() {
        WorkflowFormSchema.Compiled schema = schemas.compile("""
                {
                  "widgetList": [
                    {"type":"input","options":{"name":"reason","required":true,"minLength":2,"maxLength":12}},
                    {"type":"number","options":{"name":"days","required":true,"min":1,"max":30}},
                    {"type":"switch","options":{"name":"urgent"}},
                    {"type":"select","options":{"name":"kind","multiple":false}},
                    {"type":"checkbox","options":{"name":"tags"}}
                  ],
                  "formConfig": {}
                }
                """);

        assertThat(schema.fields()).containsOnlyKeys("reason", "days", "urgent", "kind", "tags");
        assertThat(schemas.validate(schema,
                "{\"reason\":\"annual\",\"days\":2,\"urgent\":false,\"kind\":\"paid\",\"tags\":[\"hr\"]}")
                .isObject()).isTrue();
    }

    @Test
    void rejectsMalformedDuplicateUnknownAndExecutableSchemas() {
        assertThatThrownBy(() -> schemas.compile("not-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("表单 schema");
        assertThatThrownBy(() -> schemas.compile("""
                {"widgetList":[
                  {"type":"input","options":{"name":"same"}},
                  {"type":"number","options":{"name":"same"}}
                ]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("重复");
        assertThatThrownBy(() -> schemas.compile("""
                {"widgetList":[{"type":"remote-script","options":{"name":"unsafe"}}]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持");
        assertThatThrownBy(() -> schemas.compile("""
                {"widgetList":[{"type":"input","options":{"name":"unsafe","onCreated":"() => fetch('/secret')"}}]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("可执行");
    }

    @Test
    void rejectsUnknownMissingWrongTypeAndOutOfRangeValues() {
        WorkflowFormSchema.Compiled schema = schemas.compile("""
                {"widgetList":[
                  {"type":"input","options":{"name":"reason","required":true,"minLength":2,"maxLength":4}},
                  {"type":"number","options":{"name":"days","required":true,"min":1,"max":3}},
                  {"type":"switch","options":{"name":"urgent"}}
                ]}
                """);

        assertThatThrownBy(() -> schemas.validate(schema, "{\"days\":1}"))
                .hasMessageContaining("reason").hasMessageContaining("必填");
        assertThatThrownBy(() -> schemas.validate(schema, "{\"reason\":\"ok\",\"days\":\"2\"}"))
                .hasMessageContaining("days").hasMessageContaining("数字");
        assertThatThrownBy(() -> schemas.validate(schema, "{\"reason\":\"toolong\",\"days\":2}"))
                .hasMessageContaining("reason").hasMessageContaining("长度");
        assertThatThrownBy(() -> schemas.validate(schema, "{\"reason\":\"ok\",\"days\":9}"))
                .hasMessageContaining("days").hasMessageContaining("最大值");
        assertThatThrownBy(() -> schemas.validate(schema, "{\"reason\":\"ok\",\"days\":2,\"admin\":true}"))
                .hasMessageContaining("admin").hasMessageContaining("未知字段");
    }
}
