package com.loomguard.bus;

import com.loomguard.model.Transaction;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * The offline default bus: a bounded queue drained by a pool of <b>virtual
 * threads</b>.
 *
 * <p>{@link #publish} never blocks — if the buffer is full the event is dropped
 * and counted. That counter is the backpressure signal exposed on
 * {@code /api/stats}: a slow consumer degrades visibly instead of stalling the
 * checkout path.
 */
@Component
@Profile("!kafka")
public class InMemoryEventBus implements EventBus {

    private final BlockingQueue<Transaction> queue;
    private final List<Consumer<Transaction>> handlers = new CopyOnWriteArrayList<>();
    private final AtomicLong dropped = new AtomicLong();
    private final int workers;
    private ExecutorService executor;
    private volatile boolean running = true;

    public InMemoryEventBus(
            @Value("${loomguard.bus.capacity:100000}") int capacity,
            @Value("${loomguard.bus.workers:8}") int workers) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.workers = workers;
    }

    @PostConstruct
    void start() {
        // One virtual thread per consumer worker: cheap to create, parks rather
        // than pinning a carrier thread while waiting on the queue.
        executor = Executors.newVirtualThreadPerTaskExecutor();
        for (int i = 0; i < workers; i++) {
            executor.submit(this::drain);
        }
    }

    private void drain() {
        while (running) {
            try {
                Transaction txn = queue.poll(200, TimeUnit.MILLISECONDS);
                if (txn == null) {
                    continue;
                }
                for (Consumer<Transaction> handler : handlers) {
                    try {
                        handler.accept(txn);
                    } catch (RuntimeException ignored) {
                        // a bad handler must not kill the worker
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    @Override
    public void publish(Transaction transaction) {
        if (!queue.offer(transaction)) {
            dropped.incrementAndGet();
        }
    }

    @Override
    public void subscribe(Consumer<Transaction> handler) {
        handlers.add(handler);
    }

    @Override
    public long dropped() {
        return dropped.get();
    }

    public int queueDepth() {
        return queue.size();
    }

    @PreDestroy
    void stop() {
        running = false;
        if (executor != null) {
            executor.shutdownNow();
        }
    }
}
