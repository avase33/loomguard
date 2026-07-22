package com.loomguard.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A dependency-free load generator that opens one <b>virtual thread per
 * concurrent client</b>.
 *
 * <p>This is the point of the project in miniature: spawning 10,000 platform
 * threads would exhaust memory, but 10,000 virtual threads cost about as much
 * as 10,000 objects. Run it against a live API to produce the throughput and
 * latency numbers quoted in the README.
 *
 * <pre>
 *   mvn -q compile exec:java -Dexec.mainClass=com.loomguard.loadtest.LoadHarness \
 *       -Dexec.args="http://localhost:8080 5000 20"
 * </pre>
 *
 * args: {@code <baseUrl> <concurrency> <seconds>}
 */
public final class LoadHarness {

    private static final String[] COUNTRIES = {"US", "US", "US", "GB", "DE", "RU", "NG"};
    private static final String[] MERCHANTS = {"coffee", "grocer", "fuel", "stream", "pharma", "anon"};

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "http://localhost:8080";
        int concurrency = args.length > 1 ? Integer.parseInt(args[1]) : 2000;
        int seconds = args.length > 2 ? Integer.parseInt(args[2]) : 15;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        AtomicLong ok = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        List<Long> latenciesNanos = java.util.Collections.synchronizedList(new ArrayList<>());
        AtomicInteger counter = new AtomicInteger();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds);

        System.out.printf("loomguard load: %d virtual threads -> %s for %ds%n",
                concurrency, baseUrl, seconds);
        long started = System.nanoTime();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrency; i++) {
                pool.submit(() -> {
                    Random rng = new Random(Thread.currentThread().threadId());
                    while (System.nanoTime() < deadline) {
                        String body = payload(counter.incrementAndGet(), rng);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(baseUrl + "/api/transactions"))
                                .header("Content-Type", "application/json")
                                .timeout(Duration.ofSeconds(10))
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build();
                        long t0 = System.nanoTime();
                        try {
                            HttpResponse<Void> response =
                                    client.send(request, HttpResponse.BodyHandlers.discarding());
                            long elapsed = System.nanoTime() - t0;
                            if (response.statusCode() == 200) {
                                ok.incrementAndGet();
                                latenciesNanos.add(elapsed);
                            } else {
                                failed.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failed.incrementAndGet();
                        }
                    }
                    return null;
                });
            }
        } // close() waits for every virtual thread to finish

        double elapsedSeconds = (System.nanoTime() - started) / 1_000_000_000.0;
        report(ok.get(), failed.get(), latenciesNanos, elapsedSeconds);
    }

    private static String payload(int n, Random rng) {
        boolean fraud = rng.nextDouble() < 0.03;
        String card = "c_" + rng.nextInt(500);
        double amount = fraud ? 900 + rng.nextDouble() * 700 : 15 + rng.nextDouble() * 85;
        String country = fraud ? COUNTRIES[4 + rng.nextInt(3)] : "US";
        String merchant = MERCHANTS[rng.nextInt(MERCHANTS.length)];
        return """
                {"txnId":"t_%d","userId":"u_%d","cardId":"%s","amount":%.2f,\
                "currency":"USD","merchant":"%s","country":"%s"}"""
                .formatted(n, rng.nextInt(500), card, amount, merchant, country);
    }

    private static void report(long ok, long failed, List<Long> latencies, double seconds) {
        long[] sorted;
        synchronized (latencies) {
            sorted = latencies.stream().mapToLong(Long::longValue).toArray();
        }
        Arrays.sort(sorted);

        System.out.printf("%n  requests ok : %d%n", ok);
        System.out.printf("  failed      : %d%n", failed);
        System.out.printf("  duration    : %.1fs%n", seconds);
        System.out.printf("  throughput  : %.0f req/s%n", ok / Math.max(seconds, 1e-9));
        if (sorted.length > 0) {
            System.out.printf("  p50 latency : %.2f ms%n", ms(sorted, 0.50));
            System.out.printf("  p95 latency : %.2f ms%n", ms(sorted, 0.95));
            System.out.printf("  p99 latency : %.2f ms%n", ms(sorted, 0.99));
        }
    }

    private static double ms(long[] sorted, double q) {
        int idx = (int) Math.floor(q * (sorted.length - 1));
        return sorted[idx] / 1_000_000.0;
    }

    private LoadHarness() {
    }
}
