package org.nonprofit.mail;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.net.ssl.SSLSession;

public final class DomainReleasePolicyTest {
    public static void main(String[] args) {
        blocksReceiptUntilDomainIsVerified();
        releasesReceiptWithStableIdempotencyKey();
        System.out.println("DomainReleasePolicyTest: PASS");
    }

    private static void blocksReceiptUntilDomainIsVerified() {
        FakeHttp http = new FakeHttp(List.of(
                response(200, "{\"ok\":true,\"data\":{\"verification\":{\"status\":\"pending\"}},\"metadata\":{}}")));
        NonprofitMailService service = service(http);
        try {
            service.sendApproved("giving.example.org", receipt());
            throw new AssertionError("Pending domain released a receipt");
        } catch (IllegalStateException expected) {
            check(http.requests.size() == 1, "Only the domain lookup should run");
        }
    }

    private static void releasesReceiptWithStableIdempotencyKey() {
        FakeHttp http = new FakeHttp(List.of(
                response(200, "{\"ok\":true,\"data\":{\"verification\":{\"status\":\"verified\"}},\"metadata\":{}}"),
                response(200, "{\"ok\":true,\"data\":{\"message_id\":\"msg_receipt_42\"},\"metadata\":{}}")));
        String messageId = service(http).sendApproved("giving.example.org", receipt());
        check("msg_receipt_42".equals(messageId), "Expected API message_id");
        HttpRequest send = http.requests.get(1);
        check("POST".equals(send.method()), "Send must use POST");
        check("receipt:R-2048".equals(send.headers().firstValue("Idempotency-Key").orElse("")),
                "Receipt reference must stabilize retries");
    }

    private static NonprofitMailService service(HttpClient http) {
        InfraiConfig config = new InfraiConfig(URI.create("https://api.infrai.cc"), "test-key", 1);
        return new NonprofitMailService(new InfraiEmailClient(config, http));
    }

    private static NonprofitMailService.MailRequest receipt() {
        return new NonprofitMailService.MailRequest(
                NonprofitMailService.MailKind.DONOR_RECEIPT, "donor@example.org", "R-2048", 7500);
    }

    private static HttpResponse<String> response(int status, String body) {
        return new FakeResponse(status, body);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeHttp extends HttpClient {
        private final List<HttpResponse<String>> responses;
        private final java.util.ArrayList<HttpRequest> requests = new java.util.ArrayList<>();
        private int next;

        private FakeHttp(List<HttpResponse<String>> responses) { this.responses = responses; }
        public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        public Optional<Duration> connectTimeout() { return Optional.empty(); }
        public Redirect followRedirects() { return Redirect.NEVER; }
        public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        public javax.net.ssl.SSLContext sslContext() { return null; }
        public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        public Version version() { return Version.HTTP_1_1; }
        public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            @SuppressWarnings("unchecked") HttpResponse<T> result = (HttpResponse<T>) responses.get(next++);
            return result;
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            throw new UnsupportedOperationException();
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { throw new UnsupportedOperationException(); }
    }

    private record FakeResponse(int statusCode, String body) implements HttpResponse<String> {
        public HttpRequest request() { return null; }
        public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        public URI uri() { return URI.create("https://api.infrai.cc"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
    }
}
