package app.util;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Класс управления частотой вызовов.
 * Нужен чтобы не спамить запросами и не превышать лимиты API
 * count - сколько запросов в секунду разрешено отправлять
 * Created by Aleksey Selikhov 17.10.2025
 */
public class RateLimiter {
    private final int rps;
    private final AtomicInteger tokens = new AtomicInteger(0);

    public RateLimiter(int rps) {
        this.rps = Math.max(1, rps);

        Thread thread = new Thread(() -> {
            while(true) {
                tokens.set(rps);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException exception) {
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void acquire() {
        while(true) {
            int cur = tokens.get();
            if (cur > 0) {
                if (tokens.compareAndSet(cur, cur-1)) return;
            } else {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}
            }
        }
    }
}
