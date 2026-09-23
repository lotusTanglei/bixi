package com.lotus.bixi.upms.demo.leave.config;

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
import com.lotus.bixi.upms.demo.leave.event.LeaveWorkflowEventHandler;
import com.lotus.bixi.upms.demo.leave.event.LeaveBusinessTaskEventHandler;
import com.lotus.bixi.upms.demo.leave.service.LeaveBookingService;
import com.lotus.bixi.workflow.api.config.ConditionalOnWorkflowEnabled;
import com.lotus.bixi.workflow.api.event.WorkflowEventCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Map;

/** UPMS owner wiring for durable leave workflow commands and result events. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWorkflowEnabled
@ConditionalOnProperty(prefix = "bixi.reliable", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ReliableRabbitProperties.class)
public class LeaveReliableConfiguration {
    @Bean("leaveWorkflowEventCodec")
    @ConditionalOnMissingBean(name = "leaveWorkflowEventCodec")
    WorkflowEventCodec leaveWorkflowEventCodec() { return new WorkflowEventCodec(); }

    @Bean("leaveReliableDeliveryProperties")
    @ConditionalOnMissingBean(name = "leaveReliableDeliveryProperties")
    ReliableDeliveryProperties leaveReliableDeliveryProperties(
            @Value("${bixi.reliable.delivery.poll-interval:PT1S}") Duration pollInterval,
            @Value("${bixi.reliable.delivery.lease-duration:PT30S}") Duration leaseDuration,
            @Value("${bixi.reliable.delivery.send-timeout:PT5S}") Duration sendTimeout,
            @Value("${bixi.reliable.delivery.batch-size:20}") int batchSize,
            @Value("${bixi.reliable.delivery.max-attempts:12}") int maxAttempts) {
        return new ReliableDeliveryProperties(pollInterval, leaseDuration, sendTimeout, batchSize, maxAttempts);
    }

    @Bean("upmsOutboxStore")
    JdbcOutboxStore upmsOutboxStore(DataSource dataSource, PlatformTransactionManager transactionManager,
            @Qualifier("leaveReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new JdbcOutboxStore(dataSource, transactionManager, properties);
    }

    @Bean("upmsInboxStore")
    JdbcInboxStore upmsInboxStore(DataSource dataSource, PlatformTransactionManager transactionManager,
            @Qualifier("leaveReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new JdbcInboxStore(dataSource, transactionManager, properties);
    }

    @Bean("upmsQuarantineStore")
    JdbcQuarantineStore upmsQuarantineStore(DataSource dataSource, PlatformTransactionManager transactionManager) {
        return new JdbcQuarantineStore(dataSource, transactionManager);
    }

    @Bean(name = "upmsInboxExecutor")
    InboxExecutor upmsInboxExecutor(JdbcInboxStore upmsInboxStore, LeaveWorkflowEventHandler handler,
            LeaveBusinessTaskEventHandler businessHandler) {
        return new InboxExecutor(upmsInboxStore, "upms", Map.of(
                new InboxExecutor.Route("workflow", "WORKFLOW_STARTED", 1), handler,
                new InboxExecutor.Route("workflow", "WORKFLOW_START_REJECTED", 1), handler,
                new InboxExecutor.Route("workflow", "WORKFLOW_COMPLETED", 1), handler,
                new InboxExecutor.Route("workflow", "WORKFLOW_BUSINESS_TASK_REQUESTED", 1), businessHandler,
                new InboxExecutor.Route("workflow", "WORKFLOW_COMPENSATION_REQUESTED", 1), businessHandler));
    }

    @Bean
    LeaveWorkflowEventHandler leaveWorkflowEventHandler(
            com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper leaves,
            @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
        return new LeaveWorkflowEventHandler(leaves, codec);
    }

    @Bean
    LeaveBusinessTaskEventHandler leaveBusinessTaskEventHandler(
            com.lotus.bixi.upms.demo.leave.mapper.LeaveRequestMapper leaves,
            LeaveBookingService bookings,
            @Qualifier("upmsOutboxStore") JdbcOutboxStore outbox,
            @Qualifier("leaveWorkflowEventCodec") WorkflowEventCodec codec) {
        return new LeaveBusinessTaskEventHandler(leaves, bookings, outbox, codec);
    }

    @Bean(name = "upmsTransport")
    @ConditionalOnProperty(name = "bixi.deployment.mode", havingValue = "single")
    DurableTransport upmsLocalTransport(@Qualifier("workflowInboxExecutor") InboxExecutor workflowInboxExecutor) {
        return new LocalDurableTransport(Map.of("workflow", workflowInboxExecutor));
    }

    @Bean(name = "upmsTransport")
    @Conditional(CloudRabbitCondition.class)
    RabbitOwnerEndpoint upmsRabbitEndpoint(ReliableRabbitProperties rabbit,
            @Qualifier("upmsInboxExecutor") InboxExecutor inbox,
            @Qualifier("upmsInboxStore") JdbcInboxStore upmsInboxStore,
            @Qualifier("upmsQuarantineStore") JdbcQuarantineStore upmsQuarantineStore,
            @Qualifier("leaveReliableDeliveryProperties") ReliableDeliveryProperties properties,
            @Value("${bixi.reliable.rabbit.upms-exchange:bixi.upms}") String upmsExchange,
            @Value("${bixi.reliable.rabbit.upms-routing-key:workflow.upms}") String upmsRoutingKey,
            @Value("${bixi.reliable.rabbit.workflow-exchange:bixi.workflow}") String workflowExchange,
            @Value("${bixi.reliable.rabbit.workflow-routing-key:upms.workflow}") String workflowRoutingKey,
            @Value("${bixi.reliable.rabbit.upms-queue:bixi.upms.inbox}") String upmsQueue) {
        return new RabbitOwnerEndpoint(rabbit.toSettings(),
                new RabbitDurableTransport.Route("upms", "workflow", upmsExchange, upmsRoutingKey),
                new RabbitDurableTransport.Route("workflow", "upms", workflowExchange, workflowRoutingKey),
                upmsQueue, inbox, upmsInboxStore, upmsQuarantineStore, properties, () -> true);
    }

    @Bean
    OutboxDispatcher upmsOutboxDispatcher(@Qualifier("upmsOutboxStore") JdbcOutboxStore upmsOutboxStore,
            @Qualifier("upmsTransport") DurableTransport transport,
            @Qualifier("leaveReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new OutboxDispatcher(upmsOutboxStore, "upms", transport, properties);
    }

    @Bean
    ReliableDeliveryWorker upmsReliableWorker(OutboxDispatcher upmsOutboxDispatcher,
            @Qualifier("upmsInboxExecutor") InboxExecutor upmsInboxExecutor,
            @Qualifier("leaveReliableDeliveryProperties") ReliableDeliveryProperties properties) {
        return new ReliableDeliveryWorker(upmsOutboxDispatcher, upmsInboxExecutor, properties);
    }

    static final class CloudRabbitCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return "cloud".equals(context.getEnvironment().getProperty("bixi.deployment.mode", "cloud"))
                    && "true".equalsIgnoreCase(context.getEnvironment()
                    .getProperty("bixi.reliable.rabbit.enabled", "false"));
        }
    }
}
