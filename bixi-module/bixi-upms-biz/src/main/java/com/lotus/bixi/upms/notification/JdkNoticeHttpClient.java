package com.lotus.bixi.upms.notification;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class JdkNoticeHttpClient implements NoticeHttpClient {

    @Override
    public NoticeHttpResponse post(URI uri, String body, Map<String, String> headers, Duration timeout)
            throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        headers.forEach(request::header);
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new NoticeHttpResponse(response.statusCode(), response.body());
    }
}
