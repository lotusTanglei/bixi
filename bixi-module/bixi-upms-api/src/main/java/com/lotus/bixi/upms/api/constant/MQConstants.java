package com.lotus.bixi.upms.api.constant;

/**
 * MQ 常量
 *
 * 说明：这里仅放“跨服务需要一致”的 MQ 命名（交换机/队列/路由键）。
 *
 * @author bixi
 */
public interface MQConstants {

    /**
     * 通知交换机名称
     */
    String NOTICE_EXCHANGE = "notice.exchange";

    /**
     * 通知队列名称（示例）
     */
    String NOTICE_QUEUE = "notice.queue";

    /**
     * 通知路由键（示例）
     */
    String NOTICE_ROUTING_KEY = "notice.key";

    /**
     * 系统通知队列（业务模块生产，UPMS 统一消费）
     */
    String SYS_NOTICE_QUEUE = "sys.notice.queue";

}
