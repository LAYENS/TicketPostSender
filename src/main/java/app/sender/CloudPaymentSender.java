package app.sender;

import app.builder.BuilderJson;
import app.util.RateLimiter;
import app.util.SendResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Класс обращения в API и отправка json
 *Created by Aleksey Selikhov 18.10.2025
 */
public class CloudPaymentSender {
    private final HttpClient httpClient;
    private final String apiUrl;
    private final RateLimiter rateLimiter;
    private final BuilderJson builderJson;
    private final RetryPolicy retryPolicy;
    private final ObjectMapper mapper;

    public CloudPaymentSender(HttpClient httpClient, String apiUrl,
                              RateLimiter rateLimiter,
                              BuilderJson builderJson, RetryPolicy retryPolicy,
                              ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.apiUrl = apiUrl;
        this.rateLimiter = rateLimiter;
        this.builderJson = builderJson;
        this.retryPolicy = retryPolicy;
        this.mapper = mapper;
    }

    public SendResult sendCorrection(Map<String,String> row, String publicId, String key) throws Exception {

        String authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicId + ":" + key).getBytes(StandardCharsets.UTF_8));
        rateLimiter.acquire();

        String jsonBody = builderJson.buildJSON(row);
        System.out.println(jsonBody);
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
            String body = response.body();

            if (code == 200) {
                boolean ok = true;
                try {
                    var node = mapper.readTree(body);
                    if (node.has("Success")) ok = node.get("Success").asBoolean();
                } catch (Exception ignored) {}
                return new SendResult(ok, code, body);
            } else if (code == 429 || code >= 500) {
                throw new RetryPolicy.RetryableException("Ошибка запроса: " + code);
            } else {
                return new SendResult(false, code, body);
            }
        });
    }
}
