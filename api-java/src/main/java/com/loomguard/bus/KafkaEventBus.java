package com.loomguard.bus;

import com.loomguard.model.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The production bus: Apache Kafka. Active only under the {@code kafka}
 * profile — the interface is identical to {@link InMemoryEventBus}, so nothing
 * upstream or downstream changes.
 *
 * <p>Partitioning by {@code cardId} keeps all of a card's events on one
 * partition, which is what makes the per-card sliding window correct when the
 * consumer group scales out.
 */
@Component
@Profile("kafka")
public class KafkaEventBus implements EventBus {

    public static final String TOPIC = "loomguard.transactions";

    private final KafkaTemplate<String, Transaction> template;
    private final List<Consumer<Transaction>> handlers = new CopyOnWriteArrayList<>();
    private final String topic;

    public KafkaEventBus(KafkaTemplate<String, Transaction> template,
                         @Value("${loomguard.kafka.topic:" + TOPIC + "}") String topic) {
        this.template = template;
        this.topic = topic;
    }

    @Override
    public void publish(Transaction transaction) {
        // key by card so a card's events keep their order within a partition
        template.send(topic, transaction.cardId(), transaction);
    }

    @Override
    public void subscribe(Consumer<Transaction> handler) {
        handlers.add(handler);
    }

    @Override
    public long dropped() {
        return 0; // Kafka applies real backpressure; nothing is dropped locally
    }

    @KafkaListener(topics = "${loomguard.kafka.topic:" + TOPIC + "}",
            groupId = "${loomguard.kafka.group:loomguard-aggregator}")
    void consume(Transaction transaction) {
        for (Consumer<Transaction> handler : handlers) {
            try {
                handler.accept(transaction);
            } catch (RuntimeException ignored) {
                // keep consuming
            }
        }
    }
}
