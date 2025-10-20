package app.sender;

import app.builder.BuilderJson;
import app.util.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Класс обращения в API и отправка json
 *Created by Aleksey Selikhov 18.10.2025
 */
public class CloudPaymentSender {
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String authHeader;
    private final RateLimiter rateLimiter;
    private final BuilderJson builderJson;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper mapper;

    public CloudPaymentSender(HttpClient httpClient, String apiUrl,
                              String authHeader, RateLimiter rateLimiter,
                              BuilderJson builderJson, RetryPolicy retryPolicy,
                              ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.authHeader = authHeader;
        this.rateLimiter = rateLimiter;
        this.builderJson = builderJson;
        this.retryPolicy = retryPolicy;
        this.mapper = mapper;
    }

    public boolean sendCorrection(Map<String,String> row) throws Exception {
        rateLimiter.acquire();

        String jsonBody = builderJson.buildJSON(row);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(50))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return retryPolicy.executeWithRetry(() -> {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int code = response.statusCode();

            if (code == 200) {
                var node = mapper.readTree(response.body());
                return node.path("Success").asBoolean(true);
            } else if (code == 429 || code >= 500) {
                throw new RetryPolicy.RetryableException("Ошибка запроса: " + code);
            } else {
                return false;
            }
        });
    }
}
