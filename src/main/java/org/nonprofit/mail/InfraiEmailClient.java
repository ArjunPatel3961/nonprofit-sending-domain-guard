package org.nonprofit.mail;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InfraiEmailClient {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiEmailClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiEmailClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    // Canonical call boundary: InfraiEmailClient.verifyDomain
    public Map<String, Object> verifyDomain(String domain) {
        return request("POST", "/v1/email/domain/verify", Map.of("domain", domain), "domain:" + domain);
    }

    public Map<String, Object> getDomain(String domain) {
        return request("GET", "/v1/email/domain/get/" + encode(domain), null, null);
    }

    public String send(String to, String subject, String html, String operationId) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("to", to);
        body.put("subject", subject);
        body.put("html", html);
        Map<String, Object> data = request("POST", "/v1/email/send", body, operationId);
        String messageId = Json.string(data, "message_id");
        if (messageId == null) throw new IllegalStateException("Successful send response omitted message_id");
        return messageId;
    }

    public Map<String, Object> listEvents(String messageId) {
        return request("GET", "/v1/email/event/list?message_id=" + encode(messageId), null, null);
    }

    private Map<String, Object> request(String method, String path, Map<String, String> body, String idempotencyKey) {
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(Json.object(body)));
            }

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException error) {
                throw new IllegalStateException("Email transport failed", error);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Email request interrupted", error);
            }

            Map<String, Object> envelope = Json.parseObject(response.body());
            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                pause(retryDelayMillis(response, attempt));
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = Json.child(envelope, "error");
                throw new InfraiException(Json.string(error, "code"), response.statusCode(), error);
            }
            if (response.statusCode() >= 500) {
                throw new IllegalStateException("Email transport returned HTTP " + response.statusCode());
            }
            return Json.child(envelope, "data");
        }
        throw new IllegalStateException("Retry attempts exhausted");
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(this::retryAfterMillis)
                .orElse(250L * (1L << (attempt - 1)));
    }

    private long retryAfterMillis(String value) {
        try { return Math.max(0, Long.parseLong(value.trim()) * 1_000L); }
        catch (NumberFormatException ignored) { return 1_000L; }
    }

    private void pause(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", error);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
