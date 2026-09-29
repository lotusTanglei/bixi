package com.lotus.bixi.upms.notification;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider configuration. All external channels are disabled by default;
 * enabling one without an endpoint remains a failed, observable delivery.
 * Endpoints use a small provider-neutral JSON contract so deployments can
 * connect their chosen vendor without coupling the UPMS business module to a
 * vendor SDK.
 */
@Data
@ConfigurationProperties(prefix = "bixi.notice")
public class NoticeChannelProperties {

    private Channels channels = new Channels();

    /** Default for newly created notices when no channel is supplied. */
    private String defaultChannel = "IN_APP";

    /**
     * Shared callback authentication for asynchronous provider receipts. A
     * callback is rejected when this is not explicitly enabled and configured;
     * deployments must never receive unsigned provider traffic by accident.
     */
    private Receipt receipt = new Receipt();

    @Data
    public static class Receipt {
        private boolean enabled;
        private String secret;
        private String signatureHeader = "X-Bixi-Notice-Signature";
    }

    @Data
    public static class Channels {
        private HttpChannel webhook = new HttpChannel();
        private HttpChannel email = new HttpChannel();
        private HttpChannel sms = new HttpChannel();
        private HttpChannel wechat = new HttpChannel();
    }

    @Data
    public static class HttpChannel {
        private boolean enabled;
        private String endpoint;
        private String apiKey;
        private String bearerToken;
        private String from;
        private long timeoutMillis = 10_000L;
    }
}
