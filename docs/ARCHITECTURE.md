# loomguard architecture

A real-time fraud feature store. The bottleneck this design attacks: **the
checkout path must answer in single-digit milliseconds while thousands of
requests are in flight**, and a blocking database call on that path is what
takes payments down.

```
        checkout clients
              │  POST /api/transactions   (each on a virtual thread)
              ▼
┌──────────────────────────┐   publish    ┌──────────────────────────┐
│ API · Spring Boot        │ ───────────▶ │ EventBus                 │
│ score + respond fast     │              │ in-memory ring | Kafka   │
└───────┬──────────────────┘              └───────────┬──────────────┘
        │ read features                                │ consume
        │                                              ▼
        │                            ┌──────────────────────────────┐
        │                            │ FeatureAggregator            │
        │                            │ sliding windows → features   │
        │                            └───────────┬──────────────────┘
        ▼                                        │ write
┌──────────────────────────┐                     ▼
│ FeatureStore             │◀───────  in-memory map | Cassandra
└──────────────────────────┘
        │ alerts + 1s ticks over WebSocket
        ▼
┌──────────────────────────┐
│ Dashboard · TypeScript   │
└──────────────────────────┘
```

## Why virtual threads

A platform thread costs ~1 MB of stack, so a classic servlet pool tops out
around 200–500 concurrent blocking requests; beyond that you either queue or
run out of memory. A virtual thread is a heap object scheduled onto a small
pool of carrier threads. When it blocks on I/O the JVM **unmounts** it from its
carrier, so 10,000 in-flight requests cost roughly 10,000 objects rather than
10 GB of stack.

Two places this shows up here:

1. `spring.threads.virtual.enabled=true` — Tomcat serves every request on a
   virtual thread.
2. `InMemoryEventBus` drains its queue with
   `Executors.newVirtualThreadPerTaskExecutor()`.

The load harness (`loadtest/LoadHarness.java`) deliberately mirrors this: one
virtual thread per simulated client, which is what makes a 5,000-client test
runnable on a laptop.

**The caveat worth knowing in an interview:** virtual threads help *blocking
I/O*, not CPU-bound work. They also pin to their carrier inside
`synchronized` blocks that block. That's exactly why the per-card locks in
`CardWindows` are held for microseconds around pure in-memory arithmetic and
never across I/O.

## Why the bus

Scoring must not wait on the feature store. The API publishes and returns; the
aggregator updates windows and writes the store asynchronously. In the default
profile the bus is a bounded in-process queue; under the `kafka` profile it is
a real topic, keyed by `cardId` so a card's events stay ordered within one
partition — which is what keeps the sliding window correct when the consumer
group scales out.

Backpressure is explicit: a full queue **drops and counts** rather than
blocking. Losing telemetry is survivable; stalling checkout is not.

## The feature engine

`SlidingWindow` keeps a deque of events plus running `sum` and `sumSquares`.
Both are adjusted on insert *and* on eviction, so:

    mean = sum / n
    var  = max(sumSquares / n - mean², 0)
    z    = (amount - mean) / sqrt(var)

are O(1) per event no matter how many events the 5-minute window holds.
Distinct merchant and country counts use reference-counted maps — increment on
insert, decrement on evict, `distinct = map.size()`. Only `velocity1m` walks
backwards, and it touches at most the events it reports.

## The model

Logistic regression, trained at startup on synthetic labelled features with
batch gradient descent and L2 regularisation:

    p = sigmoid(w · z + b)
    ∂loss/∂w = 1/n Σ (p - y)·z + λ·w

Features are standardised first, which both helps convergence and makes the
weights comparable — that is what makes the reason codes honest. Each feature's
signed contribution to the log-odds is `w_j · z_j`, so the drivers pushing a
transaction toward fraud are the largest positive contributions, rendered as
`velocity1m=9`.

Swapping in a trained gradient-boosted model or a remote scoring service means
implementing one method; nothing else moves.

## Why Cassandra for the store

The read is always "give me the current features for this card" — a single
partition lookup by primary key, at very high write volume with no need for
joins or transactions. That is precisely Cassandra's sweet spot. The table is
keyed by `card_id` alone so reads never touch more than one partition.

## Offline-first

Default profile: in-memory bus, in-memory store, model trained in-process. The
whole pipeline — ingest, aggregate, score, alert, dashboard — runs with
`mvn spring-boot:run` and nothing else installed. `docker compose --profile
infra up` brings up real Kafka and Cassandra, and the same code paths run
against them via `SPRING_PROFILES_ACTIVE=kafka,cassandra`.
