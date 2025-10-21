package app;


import app.builder.BuilderJson;
import app.getter.CloudPaymentsGetInfo;
import app.reader.ExcelReader;
import app.sender.CloudPaymentSender;
import app.sender.RetryPolicy;
import app.util.RateLimiter;
import app.util.SendResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Основной класс запуска приложения
 * Created by Aleksey Selikhov 17.10.2025
 */
public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        // получение данных из конфига
        Properties properties = new Properties();
        Path cfgFile = Paths.get("src/main/resources/application.properties");
        if (!Files.exists(cfgFile)) {
            System.out.println("Не найден конфигурационный файл");
            System.exit(1);
        }

        try (InputStream stream = Files.newInputStream(cfgFile)) {
            properties.load(stream);
        }

        // Параметры
        String url = properties.getProperty("apiUrl");
        String excelFile = properties.getProperty("excelFile");
        String excelKeys = properties.getProperty("excelKeys");
        int threads = Integer.parseInt(properties.getProperty("threads"));
        int requestPerSeconds = Integer.parseInt(properties.getProperty("requestPerSeconds"));
        int maxRetries = Integer.parseInt(properties.getProperty("maxRetries"));
        int initialRetryMillis = Integer.parseInt(properties.getProperty("initialRetryMillis"));

        String successLog = properties.getProperty("successLog");
        String failedLog = properties.getProperty("failedLog");

        // погрузка файла Excel
        ExcelReader reader = new ExcelReader(excelFile);
        List<Map<String, String>> rows = reader.readAllRows();
        log.info("Найдено {} чеков в основном файле", rows.size());
        // Подгрузка ключей из второго эксель
        ExcelReader keysReader = new ExcelReader(excelKeys);
        List<Map<String, String>> keyRows = keysReader.readAllRows();
        log.info("Найдено {} строк с ключами", keyRows.size());

        // Преобразуем список ключей в Map для быстрого доступа
        Map<String, String> publicIdToSecret = new HashMap<>();
        for (Map<String, String> row : keyRows) {
            String pub = row.get("publicId");
            String secret = row.get("apiSecret");
            if (pub != null && secret != null) {
                publicIdToSecret.put(pub.trim(), secret.trim());
            }
        }

        // HTTP формирование запроса и отправка в нескольких потоках из конфигурации
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        ObjectMapper mapper = new ObjectMapper();
        BuilderJson builderJson = new BuilderJson(mapper);
        RateLimiter limiter = new RateLimiter(requestPerSeconds);
        RetryPolicy retryPolicy = new RetryPolicy(maxRetries, initialRetryMillis);

        CloudPaymentSender sender = new CloudPaymentSender(
                httpClient, url, limiter, builderJson, retryPolicy, mapper
        );

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        Instant startTime = Instant.now();
        try (BufferedWriter successWriter = Files.newBufferedWriter(Paths.get(successLog));
             BufferedWriter failedWriter = Files.newBufferedWriter(Paths.get(failedLog))) {

            List<Future<?>> futures = new ArrayList<>();

            for (Map<String, String> row : rows) {
                futures.add(executor.submit(() -> {
                    try {
                        String publicId = row.get("publicId");
                        String apiSecret = publicIdToSecret.get(publicId);

                        if (apiSecret == null) {
                            log.warn("Не найден apiSecret для publicId: {}", publicId);
                            synchronized (failedWriter) {
                                failedWriter.write("Нет ключа для publicId=" + publicId);
                                failedWriter.newLine();
                            }
                            failedCount.incrementAndGet();
                            return;
                        }

                        SendResult result = sender.sendCorrection(row, publicId, apiSecret);

                        synchronized (successWriter) {
                            if (result.success) {
                                successWriter.write("HTTP " + result.httpCode + " " + result.responseBody);
                                successWriter.newLine();
                                successCount.incrementAndGet();
                            } else {
                                failedWriter.write("HTTP " + result.httpCode + " " + result.responseBody);
                                failedWriter.newLine();
                                failedCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        synchronized (failedWriter) {
                            try {
                                failedWriter.write(row.toString());
                                failedWriter.newLine();
                            } catch (IOException ignored) {}
                        }
                        failedCount.incrementAndGet();
                        log.error("Ошибка при обработке строки: {}", row, e);
                    }
                }));
            }

            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {}
            }
        }

        executor.shutdown();
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        long hours = duration.toHours();
        long minutes = duration.toMinutes();
        long seconds = duration.toSeconds();

        String formatted = String.format("%02d:%02d:%02d", hours, minutes, seconds);
        log.info("Готово. Успешно: {}, Ошибки: {}", successCount.get(), failedCount.get());
        log.info("Время выполнения: " + formatted);
    }

//    public static void main(String[] args) throws Exception {
//
//        //Достаем конфиг и пробуем его считать
//        Properties properties = new Properties();
//        Path cfgFile = Paths.get("src/resources/application.properties");
//        if (!Files.exists(cfgFile)) {
//            System.out.println("Не найден конфигурационный файл");
//            System.exit(1);
//        }
//
//        try (InputStream stream = Files.newInputStream(cfgFile)) {
//            properties.load(stream);
//        }
//        // Читаем из конфигурации
//        String publicId = properties.getProperty("publicId");
//        String apiSecret = properties.getProperty("apiSecret");
//
//        ObjectMapper mapper = new ObjectMapper();
//        HttpClient httpClient = HttpClient.newBuilder()
//                .connectTimeout(Duration.ofSeconds(15))
//                .build();
//        RateLimiter limiter = new RateLimiter(5);
//        RetryPolicy retry = new RetryPolicy(3, 1000);
//
//        CloudPaymentsGetInfo infoClient = new CloudPaymentsGetInfo(
//                httpClient,
//                publicId,
//                apiSecret,
//                mapper,
//                retry,
//                limiter
//        );
//
//        // Например, спросим TransactionId у пользователя
//        Scanner scanner = new Scanner(System.in);
//        System.out.print("Введите TransactionId: ");
//        long id = scanner.nextLong();
//
//        var response = infoClient.getPaymentInfo(id);
//        System.out.println("Ответ CloudPayments:");
//        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(response));
//    }

}
