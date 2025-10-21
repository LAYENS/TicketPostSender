package app.getter;

import app.sender.RetryPolicy;
import app.util.RateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

public class CloudPaymentsGetInfo {
    private final HttpClient client;
    private final String apiUrl;
    private final String authHeader;
    private final ObjectMapper mapper;
    private final RetryPolicy retryPolicy;
    private final RateLimiter rateLimiter;

    public CloudPaymentsGetInfo(
            HttpClient client,
            String publicId,
            String apiSecret,
            ObjectMapper mapper,
            RetryPolicy retryPolicy,
            RateLimiter rateLimiter
    ) {
        this.client = client;
        this.apiUrl = "https://api.cloudpayments.ru/payments/get";
        this.authHeader = "Basic " + Base64.getEncoder()
                .encodeToString((publicId + ":" + apiSecret).getBytes());
        this.mapper = mapper;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Получить информацию о платеже по TransactionId
     *
     * @param transactionId - идентификатор транзакции CloudPayments
     * @return JsonNode с данными ответа API
     * @throws Exception если запрос неуспешен или превышен лимит повторов
     */
    public JsonNode getPaymentInfo(long transactionId) throws Exception {
        rateLimiter.acquire(); // ограничиваем RPS

        // Формируем тело запроса
        String jsonBody = mapper.writeValueAsString(
                mapper.createObjectNode().put("TransactionId", transactionId)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Повторяем при временных ошибках
        return retryPolicy.executeWithRetry(() -> {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            int code = resp.statusCode();

            if (code == 200) {
                JsonNode node = mapper.readTree(resp.body());
                boolean success = node.path("Success").asBoolean(false);
                if (success) return node;
                else throw new RetryPolicy.RetryableException("API returned unsuccessful response");
            } else if (code == 429 || code >= 500) {
                throw new RetryPolicy.RetryableException("Retryable HTTP error: " + code);
            } else {
                throw new Exception("Unexpected response code: " + code);
            }
        });
    }
}
