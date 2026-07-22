package com.loomguard.store;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.loomguard.model.FeatureVector;
import com.loomguard.model.Features;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cassandra-backed feature store (profile {@code cassandra}).
 *
 * <p>The table is keyed by {@code card_id} alone, so every read is a single
 * partition lookup — the access pattern Cassandra is fastest at, and the reason
 * it is the right store for a hot feature path with a very high write rate.
 */
@Component
@Profile("cassandra")
public class CassandraFeatureStore implements FeatureStore {

    private static final String TABLE = "card_features";

    private final CqlSession session;
    private final AtomicLong writes = new AtomicLong();
    private PreparedStatement insert;
    private PreparedStatement select;

    public CassandraFeatureStore(CqlSession session) {
        this.session = session;
    }

    @PostConstruct
    void prepare() {
        session.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  card_id text PRIMARY KEY,
                  ts bigint,
                  count5m bigint,
                  sum5m double,
                  mean5m double,
                  std5m double,
                  velocity1m bigint,
                  amount double,
                  amount_zscore double,
                  distinct_merchants5m bigint,
                  distinct_countries5m bigint
                )""".formatted(TABLE));

        insert = session.prepare("""
                INSERT INTO %s (card_id, ts, count5m, sum5m, mean5m, std5m, velocity1m,
                                amount, amount_zscore, distinct_merchants5m, distinct_countries5m)
                VALUES (?,?,?,?,?,?,?,?,?,?,?)""".formatted(TABLE));

        select = session.prepare("SELECT * FROM %s WHERE card_id = ?".formatted(TABLE));
    }

    @Override
    public void put(FeatureVector v) {
        Features f = v.features();
        session.execute(insert.bind(
                v.cardId(), v.ts(), f.count5m(), f.sum5m(), f.mean5m(), f.std5m(),
                f.velocity1m(), f.amount(), f.amountZScore(),
                f.distinctMerchants5m(), f.distinctCountries5m()));
        writes.incrementAndGet();
    }

    @Override
    public Optional<FeatureVector> get(String cardId) {
        Row row = session.execute(select.bind(cardId)).one();
        if (row == null) {
            return Optional.empty();
        }
        Features f = new Features(
                row.getLong("count5m"),
                row.getDouble("sum5m"),
                row.getDouble("mean5m"),
                row.getDouble("std5m"),
                row.getLong("velocity1m"),
                row.getDouble("amount"),
                row.getDouble("amount_zscore"),
                row.getLong("distinct_merchants5m"),
                row.getLong("distinct_countries5m"));
        return Optional.of(new FeatureVector(cardId, row.getLong("ts"), f));
    }

    @Override
    public long size() {
        return writes.get();
    }
}
