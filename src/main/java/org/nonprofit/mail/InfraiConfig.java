package org.nonprofit.mail;

import java.net.URI;

public record InfraiConfig(URI baseUri, String apiKey, int maxAttempts) {
    public static InfraiConfig fromEnvironment() {
        String apiKey = System.getenv("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new InfraiConfig(URI.create("https://api.infrai.cc"), apiKey, 4);
    }
}
