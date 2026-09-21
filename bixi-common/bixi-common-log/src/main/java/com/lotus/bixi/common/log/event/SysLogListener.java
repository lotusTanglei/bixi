

package com.lotus.bixi.common.log.event;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lotus.bixi.common.core.jackson.BixiJavaTimeModule;
import com.lotus.bixi.common.log.config.BixiLogProperties;
import com.lotus.bixi.upms.api.entity.SysLog;
import com.lotus.bixi.upms.api.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;

import java.util.Objects;

/**
 * @author 唐磊 异步监听日志事件
 */
@Slf4j
@RequiredArgsConstructor
public class SysLogListener implements InitializingBean {

    // new 一个 避免日志脱敏策略影响全局ObjectMapper
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OperationLogService operationLogService;

    private final BixiLogProperties logProperties;

    @SneakyThrows
    @Async
    @Order
    @EventListener(SysLogEvent.class)
    public void saveSysLog(SysLogEvent event) {
        SysLogEventSource source = (SysLogEventSource) event.getSource();
        SysLog sysLog = new SysLog();
        BeanUtils.copyProperties(source, sysLog);

        // json 格式刷参数放在异步中处理，提升性能
        if (Objects.nonNull(source.getBody())) {
            // 完整解析序列化副本，使 RawValue 和 @JsonRawValue 也经过字段过滤。
            JsonNode body = objectMapper.readTree(objectMapper.writeValueAsBytes(source.getBody()));
            removeExcludedFields(body);
            String params = objectMapper.writeValueAsString(body);
            sysLog.setParams(StrUtil.subPre(params, logProperties.getMaxLength()));
        }

        operationLogService.saveLog(sysLog);
    }

    @Override
    public void afterPropertiesSet() {
        objectMapper.registerModule(new BixiJavaTimeModule());
    }

    private void removeExcludedFields(JsonNode node) {
        if (node.isObject()) {
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (logProperties.shouldExcludeField(field.getKey())) {
                    fields.remove();
                } else {
                    removeExcludedFields(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::removeExcludedFields);
        }
    }

}
