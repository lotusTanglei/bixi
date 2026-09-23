package com.lotus.bixi.workflow.config;

import com.lotus.bixi.common.mq.reliable.DurableMessageHandler;
import com.lotus.bixi.common.mq.reliable.DurableTransport;
import com.lotus.bixi.common.mq.reliable.InboxExecutor;
import com.lotus.bixi.common.mq.reliable.JdbcInboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcOutboxStore;
import com.lotus.bixi.common.mq.reliable.JdbcQuarantineStore;
import com.lotus.bixi.common.mq.reliable.LocalDurableTransport;
import com.lotus.bixi.common.mq.reliable.OutboxDispatcher;
import com.lotus.bixi.common.mq.reliable.RabbitDurableTransport;
import com.lotus.bixi.common.mq.reliable.RabbitOwnerEndpoint;
import com.lotus.bixi.common.mq.reliable.ReliableDeliveryProperties;
import com.lotus.bixi.common.mq.reliable.ReliableDeliveryWorker;
import com.lotus.bixi.common.mq.reliable.ReliableRabbitProperties;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import com.lotus.bixi.workflow.event.WorkflowEventRecorder;
import com.lotus.bixi.workflow.event.WorkflowStartRequestedHandler;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskEventPublisher;
import com.lotus.bixi.workflow.event.LeaveBusinessTaskRequestDelegate;
import com.lotus.bixi.workflow.event.WorkflowBusinessTaskResultHandler;
import com.lotus.bixi.workflow.event.LeaveCompensationRequestDelegate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

/** Explicit workflow owner wiring. No reliable component is created until opted in. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ReliableRabbitProperties.class)
public class WorkflowReliableConfiguration {
    @Bean("workflowEventCodec")
    @ConditionalOnMissingBean(name = "workflowEventCodec")
    WorkflowEventCodec workflowEventCodec() { return new WorkflowEventCodec(); }

    @Bean("workflowReliableDeliveryProperties")
    @ConditionalOnMissingBean(name = "workflowReliableDeliveryProperties")
    ReliableDeliveryProperties reliableDeliveryProperties(
            @Value("${bixi.reliable.delivery.poll-interval:PT1S}") Duration pollInterval,
            @Value("${bixi.reliable.delivery.lease-duration:PT30S}") Duration leaseDuration,
            @Value("${bixi.reliable.delivery.send-timeout:PT5S}") Duration sendTimeout,
            @Value("${bixi.reliable.delivery.batch-size:20}") int batchSize,
            @Value("${bixi.reliable.delivery.max-attempts:12}") int maxAttempts) {
        return new ReliableDeliveryProperties(pollInterval, leaseDuration, sendTimeout, batchSize, maxAttempts);
    }

    @Bean("workflowOutboxStore")
    JdbcOutboxStore workflowOutboxStore(DataSource dataSource, PlatformTransactionManager transactionManager,
            @Qualifier("workflowReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new JdbcOutboxStore(dataSource, transactionManager, properties);
    }

    @Bean("workflowInboxStore")
    JdbcInboxStore workflowInboxStore(DataSource dataSource, PlatformTransactionManager transactionManager,
            @Qualifier("workflowReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new JdbcInboxStore(dataSource, transactionManager, properties);
    }

    @Bean("workflowQuarantineStore")
    JdbcQuarantineStore workflowQuarantineStore(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcQuarantineStore(dataSource, transactionManager);
    }

    @Bean
    InboxExecutor workflowInboxExecutor(JdbcInboxStore workflowInboxStore,
            WorkflowStartRequestedHandler handler, WorkflowBusinessTaskResultHandler businessResultHandler) {
        return new InboxExecutor(workflowInboxStore, "workflow", Map.of(
                new InboxExecutor.Route("upms", "WORKFLOW_START_REQUESTED", 1), handler,
                new InboxExecutor.Route("upms", "WORKFLOW_BUSINESS_TASK_RESULT", 1), businessResultHandler,
                new InboxExecutor.Route("upms", "WORKFLOW_COMPENSATION_RESULT", 1), businessResultHandler));
    }

    @Bean
    WorkflowEventRecorder workflowEventRecorder(@Qualifier("workflowOutboxStore") JdbcOutboxStore workflowOutboxStore,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        return new WorkflowEventRecorder(workflowOutboxStore, codec);
    }

    @Bean
    WorkflowBusinessTaskEventPublisher workflowBusinessTaskEventPublisher(
            @Qualifier("workflowOutboxStore") JdbcOutboxStore workflowOutboxStore,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        return new WorkflowBusinessTaskEventPublisher(workflowOutboxStore, codec);
    }

    @Bean(name = "leaveBusinessTaskRequestDelegate")
    LeaveBusinessTaskRequestDelegate leaveBusinessTaskRequestDelegate(
            WorkflowBusinessTaskEventPublisher publisher) {
        return new LeaveBusinessTaskRequestDelegate(publisher);
    }

    @Bean(name = "leaveCompensationRequestDelegate")
    LeaveCompensationRequestDelegate leaveCompensationRequestDelegate(
            WorkflowBusinessTaskEventPublisher publisher) {
        return new LeaveCompensationRequestDelegate(publisher);
    }

    @Bean
    WorkflowStartRequestedHandler workflowStartRequestedHandler(
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec,
            com.lotus.bixi.workflow.service.impl.ProcessInstanceServiceImpl processes,
            WorkflowEventRecorder recorder) {
        return new WorkflowStartRequestedHandler(codec, processes, recorder);
    }

    @Bean
    WorkflowBusinessTaskResultHandler workflowBusinessTaskResultHandler(
            org.flowable.engine.RuntimeService runtime,
            org.flowable.engine.HistoryService history,
            @Qualifier("workflowEventCodec") WorkflowEventCodec codec) {
        return new WorkflowBusinessTaskResultHandler(runtime, history, codec);
    }

    @Bean
    @ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "single")
    @Qualifier("workflowTransport")
    DurableTransport workflowLocalTransport(@Qualifier("upmsInboxExecutor") InboxExecutor upmsInboxExecutor) {
        return new LocalDurableTransport(Map.of("upms", upmsInboxExecutor));
    }

    @Bean
    @Conditional(CloudRabbitCondition.class)
    @Qualifier("workflowTransport")
    RabbitOwnerEndpoint workflowRabbitEndpoint(ReliableRabbitProperties rabbit,
            @Qualifier("workflowInboxExecutor") InboxExecutor inbox,
            @Qualifier("workflowInboxStore") JdbcInboxStore workflowInboxStore,
            @Qualifier("workflowQuarantineStore") JdbcQuarantineStore workflowQuarantineStore,
            @Qualifier("workflowReliableDeliveryProperties") ReliableDeliveryProperties properties,
            @Value("${bixi.reliable.rabbit.workflow-exchange:bixi.workflow}") String workflowExchange,
            @Value("${bixi.reliable.rabbit.workflow-routing-key:upms.workflow}") String workflowRoutingKey,
            @Value("${bixi.reliable.rabbit.upms-exchange:bixi.upms}") String upmsExchange,
            @Value("${bixi.reliable.rabbit.upms-routing-key:workflow.upms}") String upmsRoutingKey,
            @Value("${bixi.reliable.rabbit.workflow-queue:bixi.workflow.inbox}") String workflowQueue) {
        return new RabbitOwnerEndpoint(rabbit.toSettings(),
                new RabbitDurableTransport.Route("workflow", "upms",
                        workflowExchange, workflowRoutingKey),
                new RabbitDurableTransport.Route("upms", "workflow",
                        upmsExchange, upmsRoutingKey),
                workflowQueue, inbox, workflowInboxStore,
                workflowQuarantineStore, properties, () -> true);
    }

    @Bean
    OutboxDispatcher workflowOutboxDispatcher(@Qualifier("workflowOutboxStore") JdbcOutboxStore workflowOutboxStore,
            @Qualifier("workflowTransport") DurableTransport transport,
            @Qualifier("workflowReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new OutboxDispatcher(workflowOutboxStore, "workflow", transport, properties);
    }

    @Bean
    ReliableDeliveryWorker workflowReliableWorker(OutboxDispatcher workflowOutboxDispatcher,
            @Qualifier("workflowInboxExecutor") InboxExecutor workflowInboxExecutor,
            @Qualifier("workflowReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new ReliableDeliveryWorker(workflowOutboxDispatcher, workflowInboxExecutor, properties);
    }

    static final class CloudRabbitCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String mode = context.getEnvironment().getProperty("bixi.deployment.mode", "cloud");
            return "cloud".equals(mode)
                    && "true".equalsIgnoreCase(context.getEnvironment()
                    .getProperty("bixi.reliable.rabbit.enabled", "false"));
        }
    }
}
