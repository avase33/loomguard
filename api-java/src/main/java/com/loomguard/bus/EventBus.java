package com.loomguard.bus;

import com.loomguard.model.Transaction;

import java.util.function.Consumer;

/**
 * Decouples ingestion from processing.
 *
 * <p>The default implementation is an in-process queue drained by virtual
 * threads, so the whole pipeline runs with no broker. Activating the
 * {@code kafka} profile swaps in {@link KafkaEventBus} without any change to
 * the API or the aggregator.
 */
public interface EventBus {

    /** Publishes a transaction. Must not block the caller. */
    void publish(Transaction transaction);

    /** Registers a handler invoked for every published transaction. */
    void subscribe(Consumer<Transaction> handler);

    /** Events dropped because the buffer was full (backpressure signal). */
    long dropped();
}
