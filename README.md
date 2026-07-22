# loomguard 🛡️

**A high-throughput fraud detection API and real-time feature store**, built on
**Java 21 virtual threads**. Checkout events arrive over REST, an event bus
decouples ingestion from processing, a from-scratch sliding-window engine keeps
per-card features fresh at O(1) per event, and a from-scratch logistic model
scores every transaction with explainable reason codes.

```
checkout ──▶ Spring Boot API ──▶ event bus ──▶ aggregator ──▶ feature store
              (virtual threads)   (in-mem/Kafka)  (sliding windows)  (in-mem/Cassandra)
                     │                                  │
                     └──── WebSocket /ws/alerts ────▶ React dashboard
```

| Layer | Technology | Owns |
| --- | --- | --- |
| **API** | Java 21 · Spring Boot 3.3 | Ingest, scoring, virtual-thread concurrency |
| **Bus** | In-memory ring *(default)* · Apache Kafka | Decoupling + backpressure |
| **Store** | ConcurrentHashMap *(default)* · Apache Cassandra | Low-latency feature reads |
| **Model** | Pure Java | Logistic regression + reason codes, no ML dependency |
| **Dashboard** | TypeScript · Next.js | Live throughput, p99 latency, alert feed |

**The default profile runs the entire pipeline with no external services** — no
Kafka, no Cassandra, no ML library. Real infrastructure is one profile away.

## Quickstart

```bash
cd api-java && mvn spring-boot:run     # API on :8080, zero dependencies
cd dashboard-ts && npm install && npm run dev   # dashboard on :3000
```

Score a transaction:

```bash
curl -XPOST localhost:8080/api/transactions -H 'content-type: application/json' \
  -d '{"txnId":"t1","userId":"u_42","cardId":"c_7781","amount":1450,"merchant":"anon","country":"RU"}'
```

```json
{"txnId":"t1","cardId":"c_7781","probability":0.9x,"decision":"BLOCK",
 "reasons":["amount=1450","velocity1m=1","amountZScore=0"],"latencyMs":0.4}
```

## Proving the concurrency claim

The load harness opens **one virtual thread per simulated client** — the same
technology the server uses:

```bash
make run                      # terminal 1
make load CONC=5000 SECS=20   # terminal 2
```

```
loomguard load: 5000 virtual threads -> http://localhost:8080 for 20s
  requests ok : ...
  throughput  : ... req/s
  p50 latency : ... ms
  p99 latency : ... ms
```

Spawning 5,000 *platform* threads would cost gigabytes of stack; 5,000 virtual
threads cost about as much as 5,000 objects. Record your numbers here — they are
the most interview-useful thing in the repo.

## Running against real infrastructure

```bash
docker compose --profile infra up --build      # Kafka + Cassandra
# then start the API with:
SPRING_PROFILES_ACTIVE=kafka,cassandra mvn spring-boot:run
```

Nothing in the API, aggregator, or dashboard changes — `EventBus` and
`FeatureStore` are interfaces, and the profiles swap the implementations.

## The interesting engineering

- **Virtual threads end to end** — `spring.threads.virtual.enabled=true` puts
  every request on a virtual thread, and the in-memory bus drains its queue with
  `Executors.newVirtualThreadPerTaskExecutor()`. Blocking parks a virtual
  thread instead of pinning a carrier thread.
- **O(1) sliding windows** — rolling sum and sum-of-squares are updated
  incrementally on push *and* eviction, so count / mean / std / z-score are
  constant-time at any window size. Distinct merchant and country counts use
  reference-counted maps. `window/SlidingWindow.java`
- **Per-card lock striping** — `ConcurrentHashMap` for lookup, per-window
  monitors for mutation, so different cards never contend. `window/CardWindows.java`
- **Logistic regression from scratch** — standardisation, batch gradient descent
  with L2, and reason codes read off the signed contribution `w·z` of each
  feature. No ML dependency. `scoring/`
- **Backpressure that degrades visibly** — a full bus drops and counts events
  rather than blocking checkout; the counter is on `/api/stats`.

## Testing

```bash
make test        # or: cd api-java && mvn test
```

Covers window correctness (eviction, velocity, z-score), the O(1) claim under
10k events, sigmoid stability, calm-vs-fraud separation, reason-code validity,
and holdout accuracy > 90%.

## Layout

```
proto/protocol.md   shared JSON contract
api-java/           Spring Boot API, bus, store, windows, model, load harness
dashboard-ts/       Next.js live operations dashboard
docs/ARCHITECTURE.md
```

## Version note

Built against **Spring Boot 3.3 / Java 21**. If you move to a newer Spring
Boot or adopt Spring AI, the `EventBus` / `FeatureStore` seams are where the
changes land — the domain logic has no framework dependency.

## License

MIT © 2026 Akhil Vase
