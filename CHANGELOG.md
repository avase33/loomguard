# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/); versioning: [SemVer](https://semver.org/).

## [0.1.0] - 2026-07-18

Initial release — a Java 21 virtual-thread fraud detection API and feature store.

### Added
- **Spring Boot 3.3 API** on Java 21 with virtual threads enabled end to end:
  `POST /api/transactions`, `GET /api/features/{cardId}`, `GET /api/stats`.
- **From-scratch sliding-window engine**: incremental rolling sum and
  sum-of-squares give O(1) count / mean / std / z-score per event, plus
  reference-counted distinct merchant and country counts and a 1-minute
  velocity burst detector. Per-card lock striping for concurrent updates.
- **From-scratch logistic regression**: feature standardisation, batch gradient
  descent with L2, numerically stable sigmoid, and model-derived reason codes
  from each feature's signed log-odds contribution. No ML dependency.
- **Pluggable event bus**: bounded in-memory queue drained by virtual threads
  (default, with drop-and-count backpressure) or Apache Kafka keyed by card id
  under the `kafka` profile.
- **Pluggable feature store**: concurrent map (default) or Apache Cassandra
  under the `cassandra` profile, keyed for single-partition reads.
- **WebSocket dashboard feed**: immediate alerts plus per-second throughput and
  p50/p99 latency ticks; Next.js dashboard with a latency sparkline against a
  10 ms budget and a live alert table.
- **Virtual-thread load harness**: one virtual thread per simulated client,
  reporting throughput and p50/p95/p99 latency.
- JUnit tests for window correctness and model quality, docker-compose with an
  optional real-infrastructure profile, GitHub Actions CI, Makefile, MIT license.
