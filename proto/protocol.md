# loomguard wire protocol

One JSON contract across the API, the event bus, the feature store, and the
dashboard.

## 1. Transaction (client → `POST /api/transactions`)

```json
{
  "txnId": "t_00013",
  "userId": "u_042",
  "cardId": "c_7781",
  "amount": 129.50,
  "currency": "USD",
  "merchant": "m_017",
  "country": "US",
  "ts": 1752710400123
}
```

`ts` is epoch **milliseconds**; the API stamps arrival time when it is omitted.

## 2. FeatureVector (aggregator → feature store)

Computed sliding-window features, keyed by `cardId`.

```json
{
  "cardId": "c_7781",
  "ts": 1752710400123,
  "features": {
    "count5m": 7,
    "sum5m": 903.5,
    "mean5m": 129.07,
    "std5m": 44.2,
    "velocity1m": 3,
    "amount": 129.50,
    "amountZScore": 0.71,
    "distinctMerchants5m": 4,
    "distinctCountries5m": 2
  }
}
```

| feature | meaning |
| --- | --- |
| `count5m` | transactions in the trailing 300 s |
| `sum5m` / `mean5m` / `std5m` | rolling sum / mean / population std of `amount` |
| `velocity1m` | transactions in the trailing 60 s (burst detector) |
| `amountZScore` | `(amount - mean5m) / std5m`, 0 when `std5m == 0` |
| `distinctMerchants5m` / `distinctCountries5m` | unique values in the 5 m window |

## 3. FraudScore (API response, and pushed to the dashboard)

```json
{
  "txnId": "t_00013",
  "cardId": "c_7781",
  "ts": 1752710400123,
  "probability": 0.87,
  "decision": "REVIEW",
  "reasons": ["velocity1m=9", "amountZScore=4.2", "distinctCountries5m=3"],
  "latencyMs": 3.9
}
```

`decision` ∈ `ALLOW` (`p < 0.5`), `REVIEW` (`0.5 ≤ p < 0.85`), `BLOCK` (`p ≥ 0.85`).

## 4. Dashboard frames (server → browser, WebSocket `/ws/alerts`)

```json
{ "type": "alert", "score": { ...FraudScore... } }
```

```json
{
  "type": "tick",
  "ts": 1752710400500,
  "ingestPerSec": 1840,
  "p50LatencyMs": 2.1,
  "p99LatencyMs": 8.7,
  "processed": 154203,
  "activeCards": 812
}
```

Alerts are pushed immediately for any non-`ALLOW` decision; ticks every second.

## Endpoints & ports

| service | port | routes |
| --- | --- | --- |
| Java API | 8080 | `POST /api/transactions`, `GET /api/features/{cardId}`, `GET /api/stats`, `GET /actuator/health`, WS `/ws/alerts` |
| TS dashboard | 3000 | — |
| Kafka (profile `kafka`) | 9092 | topic `loomguard.transactions` |
| Cassandra (profile `cassandra`) | 9042 | keyspace `loomguard`, table `card_features` |

## Profiles

| profile | bus | feature store |
| --- | --- | --- |
| *(default)* | in-memory ring | in-memory (ConcurrentHashMap) |
| `kafka` | Apache Kafka | unchanged |
| `cassandra` | unchanged | Apache Cassandra |

The default profile runs the whole pipeline with **no external services**.
