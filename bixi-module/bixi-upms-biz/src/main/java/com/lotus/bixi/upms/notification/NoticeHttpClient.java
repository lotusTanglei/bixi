package com.lotus.bixi.upms.notification;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/** Small seam around JDK HTTP so channel adapters can be tested without a network. */
@FunctionalInterface
public interface NoticeHttpClient {

    NoticeHttpResponse post(URI uri, String body, Map<String, String> headers, Duration timeout)
            throws Exception;
}
