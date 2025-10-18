package app.sender;

/**
 * Универсальный класс
 * Created by Aleksey Selikhov 18.10.2025
 */
public class RetryPolicy {
    private final int maxRetries;
    private final long initialDelayMs;

    public RetryPolicy(int maxRetries, long initialDelayMs) {
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
    }

    public <T> T executeWithRetry(RetryableOperation<T> operation) throws Exception {
        int attempt = 0;
        long delay = initialDelayMs;

        while (true) {
            attempt++;
            try {
                return operation.run();
            } catch (RetryableException ex) {
                if (attempt >= maxRetries) throw ex;
                Thread.sleep(delay);
                delay *= 2;
            }
        }
    }
    @FunctionalInterface
    public interface RetryableOperation<T> {
        T run() throws Exception;
    }

    public static class RetryableException extends Exception {
        public RetryableException(String msg) {
            super(msg);
        }
    }
}
