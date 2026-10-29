# OurNight Performance Load Tests

Constrained performance tests for the POS realtime notification guarantees.

## Scenarios

- `idleConnectionBaseline`: 0 → 25 → 50 → 75 → 100 stores, four SSE connections per store.
- `evenTrafficStress`: 25 → 50 → 100 → 150 → 200 → 250 committed events/s across 100 stores.
- `hotStoreTrafficStress`: the same total rate with 80% sent to 10 stores.
- `slowClientIsolation`: a loadtest-only fault pauses and saturates one outbound queue per store.
- `reconnectStormByReplayGap`: fresh clients per replay gap 0, 100, 300, and 301.
- `recoveryTraffic`: `/changes` plus full POS baseline after `RELOAD_REQUIRED`.
- `posCommandUnderEventStress`: real confirm/cook-complete/serve commands under max event traffic.

Every stress rate and reconnect gap starts with a fresh SSE group after the previous server-side connection count reaches zero.

## Run

```bash
node load-tests/run-constrained-slo.mjs
```

Default resource limits: app `2 CPU / 1 GiB`, Postgres `1 CPU / 1 GiB`, Redis `0.5 CPU / 256 MiB`.

Use this only to verify wiring, not as a capacity result:

```bash
STORE_COUNT=2 \
TABLES_PER_STORE=4 \
POS_CONNECTIONS_PER_STORE=2 \
BASELINE_STORE_STEPS=0,1,2 \
BASELINE_STEP_SECONDS=2 \
BASELINE_HOLD_SECONDS=2 \
STRESS_EVENT_RATES=5,10 \
STRESS_STEP_SECONDS=2 \
SLOW_CLIENT_SECONDS=2 \
RECONNECT_REPLAY_GAPS=0,2,301 \
POS_COMMAND_SAMPLES=2 \
POS_STRESS_SECONDS=8 \
node load-tests/run-constrained-slo.mjs
```

Artifacts are written to `load-tests/results/<RUN_ID>/`: `result.json`, `metrics.csv`, and `summary.md`.

## External Metrics

The runner collects app metrics from Spring Actuator and infrastructure metrics from Docker containers by default:

- Postgres: `docker exec postgres psql -U postgres -d ournight_local`
- Redis: `docker exec redis redis-cli`

Override these when needed:

```bash
POSTGRES_CONTAINER=my-postgres REDIS_CONTAINER=my-redis node load-tests/ournight-boundary-load.mjs
```

or disable them:

```bash
POSTGRES_COLLECTOR=off REDIS_COLLECTOR=off node load-tests/ournight-boundary-load.mjs
```

Collected Postgres signals:

- `pg_stat_activity`: total/active/waiting connections, longest active query age.
- `pg_locks`: ungranted lock count.
- `pg_stat_statements`: top slow queries, only when the extension is installed.
- `pos_event_outbox`: pending count, oldest pending age, published count, failure/attempt count, relay rate, and relay p50/p95/p99.

Collected Redis signals:

- `INFO stats` and `INFO clients`: ops/sec, errors, rejected/connected/blocked connections, hit/miss, expired/evicted keys.
- `LATENCY LATEST`: latest latency events when Redis latency monitoring is enabled.

The constrained runner compiles and mounts the test-only
`LoadTestNotificationMetricsController`. It exposes active SSE connections and
broadcast/outbound/catch-up queue depths only inside the `loadtest` container;
no production source is changed. Each traffic step reports its own
outbox-created-to-client p95/p99. SSE payloads count `RELOAD_REQUIRED`;
container logs provide slow-consumer, disconnect, queue-overflow, and relay-failure counts.

`metrics.csv` also includes JVM heap/thread/GC/CPU, open/max file descriptors,
Hikari active/pending/timeout, Redis clients, and outbox/runtime queue samples.
Scenario JSON includes client-side REST p95/p99 and total/server error rates.

## Result Interpretation

Use the JSON output to find the first step where:

- Notification `p95 > 1s` or `p99 > 2s`.
- POS command `p95 > 500ms` or `p99 > 1s`.
- Reconnect sync `p95 > 3s` or `p99 > 5s`.
- Healthy event loss, duplicate domain effect, stream-order violation, or healthy disconnect is non-zero.
- Outbox does not drain to zero after a stress step.

Also watch external metrics while the script runs:

- Hikari pool wait/active connections.
- Redis latency and rejected/slow commands.
- JVM heap, GC pauses, and CPU.
- Postgres locks and slow queries, especially queries missing `storeId` indexes.
