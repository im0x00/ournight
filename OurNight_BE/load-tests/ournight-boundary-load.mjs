#!/usr/bin/env node

import { randomUUID } from "node:crypto";
import { execFile } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { promisify } from "node:util";

const BASE_URL = env("BASE_URL", "http://localhost:8080");
const RUN_ID = env("RUN_ID", `lt-${Date.now()}`);
const STORE_COUNT = intEnv("STORE_COUNT", 1);
const TABLES_PER_STORE = intEnv("TABLES_PER_STORE", 30);
const CUSTOMERS_PER_STORE = intEnv("CUSTOMERS_PER_STORE", 500);
const POS_CONNECTIONS_PER_STORE = intEnv("POS_CONNECTIONS_PER_STORE", 5);
const SCENARIO = env("SCENARIO", "performance");
const OUTPUT_DIR = env("OUTPUT_DIR", join("load-tests", "results", RUN_ID));
const METRIC_INTERVAL_MS = intEnv("METRIC_INTERVAL_MS", 5000);
const PROGRESS_INTERVAL_MS = intEnv("PROGRESS_INTERVAL_MS", 5000);
const POSTGRES_COLLECTOR = env("POSTGRES_COLLECTOR", "docker");
const POSTGRES_CONTAINER = env("POSTGRES_CONTAINER", "postgres");
const POSTGRES_DB = env("POSTGRES_DB", "ournight_local");
const POSTGRES_USER = env("POSTGRES_USER", "postgres");
const REDIS_COLLECTOR = env("REDIS_COLLECTOR", "docker");
const REDIS_CONTAINER = env("REDIS_CONTAINER", "redis");
const APP_CONTAINER = env("APP_CONTAINER", "app");
const POS_COMMAND_SAMPLES = intEnv("POS_COMMAND_SAMPLES", 100);
const POS_COMMAND_ARRIVAL_INTERVAL_MS = intEnv("POS_COMMAND_ARRIVAL_INTERVAL_MS", 200);
const BASELINE_STORE_STEPS = parseList(env("BASELINE_STORE_STEPS", "0,25,50,75,100"));
const BASELINE_STEP_SECONDS = intEnv("BASELINE_STEP_SECONDS", 300);
const BASELINE_HOLD_SECONDS = intEnv("BASELINE_HOLD_SECONDS", 1800);
const STRESS_EVENT_RATES = parseList(env("STRESS_EVENT_RATES", "25,50,100,150,200,250"));
const STRESS_STEP_SECONDS = intEnv("STRESS_STEP_SECONDS", 60);
const HOT_STORE_COUNT = intEnv("HOT_STORE_COUNT", 10);
const RECONNECT_REPLAY_GAPS = parseList(env("RECONNECT_REPLAY_GAPS", "0,100,300,301"));
const HEARTBEAT_DEADLINE_MS = intEnv("HEARTBEAT_DEADLINE_MS", 45000);
const execFileAsync = promisify(execFile);
const deliveryReceipts = [];
const intentionalSlowConnectionIds = new Set();
let reloadRequiredClientEvents = 0;
let progress;

const results = {
  runId: RUN_ID,
  baseUrl: BASE_URL,
  startedAt: new Date().toISOString(),
  environment: {
    scenario: SCENARIO,
    storeCount: STORE_COUNT,
    tablesPerStore: TABLES_PER_STORE,
    customersPerStore: CUSTOMERS_PER_STORE,
    posConnectionsPerStore: POS_CONNECTIONS_PER_STORE,
    metricIntervalMs: METRIC_INTERVAL_MS,
    progressIntervalMs: PROGRESS_INTERVAL_MS,
    postgresCollector: POSTGRES_COLLECTOR,
    postgresContainer: POSTGRES_CONTAINER,
    postgresDb: POSTGRES_DB,
    redisCollector: REDIS_COLLECTOR,
    redisContainer: REDIS_CONTAINER,
    appContainer: APP_CONTAINER,
    posCommandSamples: POS_COMMAND_SAMPLES,
    posCommandArrivalIntervalMs: POS_COMMAND_ARRIVAL_INTERVAL_MS,
    baselineStoreSteps: BASELINE_STORE_STEPS,
    baselineStepSeconds: BASELINE_STEP_SECONDS,
    baselineHoldSeconds: BASELINE_HOLD_SECONDS,
    stressEventRates: STRESS_EVENT_RATES,
    stressStepSeconds: STRESS_STEP_SECONDS,
    hotStoreCount: HOT_STORE_COUNT,
    reconnectReplayGaps: RECONNECT_REPLAY_GAPS,
    resourceLimits: {
      appCpus: env("SLO_APP_CPUS", "unbounded"),
      appMemory: env("SLO_APP_MEMORY", "unbounded"),
      postgresCpus: env("SLO_POSTGRES_CPUS", "unbounded"),
      postgresMemory: env("SLO_POSTGRES_MEMORY", "unbounded"),
      redisCpus: env("SLO_REDIS_CPUS", "unbounded"),
      redisMemory: env("SLO_REDIS_MEMORY", "unbounded"),
    },
  },
  notes: [],
  scenarios: {},
  samples: [],
};

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});

async function main() {
  if (SCENARIO !== "performance") {
    throw new Error(`Unsupported SCENARIO=${SCENARIO}; this runner only supports performance`);
  }
  await mkdir(OUTPUT_DIR, { recursive: true });
  const sampler = new ActuatorSampler(BASE_URL, METRIC_INTERVAL_MS);
  progress = new ProgressReporter(PROGRESS_INTERVAL_MS);
  sampler.start();
  progress.start();
  console.log(`[load] creating fixture runId=${RUN_ID}`);
  try {
    const fixture = await postJson("/__load-test/fixtures/festival", {
      runId: RUN_ID,
      storeCount: STORE_COUNT,
      tablesPerStore: TABLES_PER_STORE,
      customersPerStore: CUSTOMERS_PER_STORE,
    });
    results.fixture = summarizeFixture(fixture);
    results.notes.push("call request API is currently commented out in the backend, so call RPS is not exercised."
    await runPerformanceSuite(fixture, sampler);
  } finally {
    results.samples = await sampler.stop();
    progress.stop();
  }

  results.runtimeSignals = await collectRuntimeSignals(results.startedAt);
  results.finishedAt = new Date().toISOString();
  results.performanceGuarantees = assessPerformanceGuarantees(results);
  results.assessment = assessResults(results);
  await writeArtifacts(results);
  console.log(JSON.stringify(results, null, 2));
  if (!results.assessment.passedMinimumGate) process.exitCode = 1;
}

async function runPerformanceSuite(fixture, sampler) {
  results.notes.push("tap visual feedback is a declared browser SLO and is not measured by this backend load suite.");
  results.notes.push("duplicate domain effect is a frontend reducer invariant and is not measured by this backend SSE client.");
  results.notes.push("Each stress step uses fresh SSE connections after the previous server-side connections reach zero.");
  await commitLoadTestEvents(
    fixture.stores.map((store, index) => ({
      storeId: store.storeId,
      logicalEventId: `${RUN_ID}-stream-prime-${index}`,
    })),
  );
  await waitForOutboxDrain(30_000);

  const baselineMetrics = new Metrics("idle-baseline-sse-clients");
  const baseline = await idleConnectionBaseline(fixture, sampler, baselineMetrics);
  results.scenarios.idleConnectionBaseline = baseline.steps;
  await closeSseGroupAndWait(baseline.connections);

  results.scenarios.evenTrafficStress = await eventRateStress({
    name: "even-traffic-stress",
    fixture,
    sampler,
    selectStore: (index, stores) => stores[index % stores.length],
  });

  const hotStores = fixture.stores.slice(0, Math.min(HOT_STORE_COUNT, fixture.stores.length));
  const coldStores = fixture.stores.slice(hotStores.length);
  results.scenarios.hotStoreTrafficStress = await eventRateStress({
    name: "hot-store-traffic-stress",
    fixture,
    sampler,
    selectStore: (index) => {
      if (index % 10 < 8 || coldStores.length === 0) {
        return hotStores[index % hotStores.length];
      }
      return coldStores[index % coldStores.length];
    },
  });

  results.scenarios.slowClientIsolation = await slowClientIsolation(fixture, sampler);

  const reconnect = await reconnectStormByReplayGap(fixture, sampler);
  results.scenarios.reconnectStormByReplayGap = reconnect.steps;
  results.scenarios.recoveryTraffic = reconnect.recovery;

  sampler.setPhase("pos-command-under-event-stress");
  const stressSeconds = intEnv("POS_STRESS_SECONDS", 60);
  const trafficPromise = generateEventTraffic({
    name: "pos-command-background",
    stores: fixture.stores,
    rate: Math.max(...STRESS_EVENT_RATES),
    seconds: stressSeconds,
    selectStore: (index, stores) => stores[index % stores.length],
  });
  const [posCommands, backgroundTraffic] = await Promise.all([
    posCommandUnderLoad(fixture.stores[0], fixture.hostUserId),
    trafficPromise,
  ]);
  const posDrain = await waitForOutboxDrain(Math.max(30_000, stressSeconds * 2_000));
  results.scenarios.posCommandUnderEventStress = {
    ...posCommands,
    backgroundEventRate: Math.max(...STRESS_EVENT_RATES),
    backgroundCommittedEvents: backgroundTraffic.committed,
    outboxPendingAfterStep: posDrain.pendingCount,
  };
  sampler.setPhase("finished");
}

async function idleConnectionBaseline(fixture, sampler, sseMetrics) {
  const steps = [];
  const connections = [];
  let openedStores = 0;
  const settleSeconds = intEnv("BASELINE_SETTLE_SECONDS", 10);

  for (const requestedStores of BASELINE_STORE_STEPS) {
    const targetStores = Math.min(requestedStores, fixture.stores.length);
    sampler.setPhase(`idle-connection-baseline-${targetStores}-stores`);
    const newStores = fixture.stores.slice(openedStores, targetStores);
    const opened = await Promise.all(
      newStores.map((store) =>
        openSseGroup(store.storeId, fixture.hostUserId, POS_CONNECTIONS_PER_STORE, sseMetrics)
      ),
    );
    connections.push(...opened.flat());
    openedStores = targetStores;
    if (newStores.length > 0) await sleep(settleSeconds * 1000);

    const controlMetrics = new Metrics(`idle-control-rest-${targetStores}-stores`);
    progress.phase(
      `idle connection baseline ${targetStores} stores / ${connections.length} connections`,
      BASELINE_STEP_SECONDS,
      controlMetrics,
    );
    await runControlRestWindow(
      fixture.stores.slice(0, Math.max(1, targetStores)),
      fixture.hostUserId,
      BASELINE_STEP_SECONDS,
      controlMetrics,
    );
    progress.phaseDone();
    steps.push(await idleBaselineSnapshot(
      `${targetStores}-stores`,
      targetStores,
      connections,
      controlMetrics,
    ));
  }

  sampler.setPhase("idle-connection-baseline-plateau-hold");
  const holdMetrics = new Metrics("idle-control-rest-plateau-hold");
  progress.phase(
    `idle plateau hold ${connections.length} connections`,
    BASELINE_HOLD_SECONDS,
    holdMetrics,
  );
  await runControlRestWindow(
    fixture.stores,
    fixture.hostUserId,
    BASELINE_HOLD_SECONDS,
    holdMetrics,
  );
  progress.phaseDone();
  steps.push(await idleBaselineSnapshot(
    "plateau-hold",
    openedStores,
    connections,
    holdMetrics,
  ));

  return { steps, connections };
}

async function runControlRestWindow(stores, hostUserId, seconds, metrics) {
  const stopAt = Date.now() + seconds * 1000;
  let index = 0;
  do {
    const store = stores[index % stores.length];
    try {
      await timed(metrics, "controlRest.pendingPayments", () =>
        getJson(`/api/pos/${store.storeId}/hall/orders/pending-payments`, hostUserId)
      );
    } catch {
      // Metrics already records the error; the window continues.
    }
    index += 1;
    if (Date.now() < stopAt) await sleep(Math.min(1000, stopAt - Date.now()));
  } while (Date.now() < stopAt);
}

async function idleBaselineSnapshot(label, stores, connections, metrics) {
  const now = Date.now();
  const maxHeartbeatGapMs = connections.length
    ? Math.max(...connections.map(({ state }) =>
        Math.max(state.maxSilenceMs, now - state.lastActivityAtMs)
      ))
    : 0;
  const runtime = await fetchJson(`${BASE_URL}/__load-test/notification-metrics`);
  const hikari = await fetchMetric(BASE_URL, "hikaricp.connections.active");
  return metrics.snapshot({
    label,
    targetStores: stores,
    expectedConnections: stores * POS_CONNECTIONS_PER_STORE,
    clientOpenConnections: connections.filter(({ state }) => !state.serverDisconnected).length,
    serverActiveConnections: runtime.activeSseConnections,
    maxHeartbeatGapMs,
    heartbeatDeadlineMs: HEARTBEAT_DEADLINE_MS,
    reloadRequiredCount: connections.reduce((sum, { state }) => sum + state.reloadRequiredCount, 0),
    serverDisconnectedClients: connections.filter(({ state }) => state.serverDisconnected).length,
    hikariActive: hikari?.value ?? null,
  });
}

async function eventRateStress({ name, fixture, sampler, selectStore }) {
  const steps = [];
  for (const rate of STRESS_EVENT_RATES) {
    sampler.setPhase(`${name}-${rate}-events-per-second`);
    const metrics = new Metrics(`${name}-${rate}-sse-clients`);
    const connections = await openAllSseClients(fixture, metrics);
    try {
      const before = captureConnectionCounters(connections);
      const delayStart = deliveryReceipts.length;
      const traffic = await generateEventTraffic({
        name: `${name}-${rate}`,
        stores: fixture.stores,
        rate,
        seconds: STRESS_STEP_SECONDS,
        selectStore,
      });
      const drain = await waitForOutboxDrain(Math.max(30_000, STRESS_STEP_SECONDS * 2_000));
      await waitForLogicalDelivery(connections, traffic.expectedByStore, 10_000);
      const guarantee = logicalDeliverySnapshot(connections, traffic.expectedByStore, before);
      const delays = deliveryReceipts.slice(delayStart)
        .map((receipt) => receipt.receivedAtMs - traffic.createdAtById.get(receipt.logicalEventId))
        .filter((value) => Number.isFinite(value) && value >= 0);
      steps.push({
        label: `${rate}-events-per-second`,
        isolatedConnectionGroup: true,
        targetEventRate: rate,
        committedLogicalEvents: traffic.committed,
        achievedInputRate: traffic.committed / STRESS_STEP_SECONDS,
        outboxPendingAfterStep: drain.pendingCount,
        ...guarantee,
        sse: {
          errors: guarantee.healthyClientDisconnects,
          delayP95Ms: percentile(delays, 95),
          delayP99Ms: percentile(delays, 99),
        },
      });
    } finally {
      await closeSseGroupAndWait(connections);
    }
  }
  return steps;
}

async function slowClientIsolation(fixture, sampler) {
  sampler.setPhase("slow-client-isolation");
  const metrics = new Metrics("slow-client-isolation-sse-clients");
  const connections = await openAllSseClients(fixture, metrics);
  const slowConnections = [];
  try {
    for (const store of fixture.stores) {
      const slow = connections.find(({ state }) =>
        state.storeId === store.storeId && state.clientSlot === 0
      );
      if (slow) {
        slow.state.pauseReads = true;
        slow.state.intentionallySlow = true;
        intentionalSlowConnectionIds.add(slow.state.connectionId);
        slowConnections.push(slow);
      }
    }
    const before = captureConnectionCounters(connections);
    const fault = await postJson("/__load-test/notification-metrics/faults/saturate-outbound", {
      connectionIds: slowConnections.map(({ state }) => state.connectionId),
    });
    if (
      fault.saturatedConnections !== slowConnections.length ||
      fault.overflowedConnections !== slowConnections.length
    ) {
      throw new Error(
        `Backpressure overflow applied to ${fault.overflowedConnections}/${slowConnections.length} clients`,
      );
    }

    const rate = intEnv("SLOW_CLIENT_EVENT_RATE", Math.max(...STRESS_EVENT_RATES));
    const seconds = intEnv("SLOW_CLIENT_SECONDS", STRESS_STEP_SECONDS);
    const traffic = await generateEventTraffic({
      name: "slow-client-isolation",
      stores: fixture.stores,
      rate,
      seconds,
      selectStore: (index, stores) => stores[index % stores.length],
    });
    const drain = await waitForOutboxDrain(Math.max(30_000, seconds * 2_000));
    const healthyConnections = connections.filter(({ state }) => !state.intentionallySlow);
    await waitForLogicalDelivery(healthyConnections, traffic.expectedByStore, 10_000);
    await waitForServerConnectionCount(healthyConnections.length, 10_000);
    const snapshot = logicalDeliverySnapshot(connections, traffic.expectedByStore, before);
    return {
      label: "one-saturated-outbound-queue-per-store",
      isolatedConnectionGroup: true,
      backpressureModel: "loadtest-only paused sender plus saturated bounded outbound queue",
      slowClients: slowConnections.length,
      saturatedConnections: fault.saturatedConnections,
      queueOverflowDisconnects: fault.overflowedConnections,
      committedLogicalEvents: traffic.committed,
      outboxPendingAfterStep: drain.pendingCount,
      ...snapshot,
    };
  } finally {
    await closeSseGroupAndWait(connections);
  }
}

async function generateEventTraffic({ name, stores, rate, seconds, selectStore }) {
  const expectedByStore = new Map();
  const createdAtById = new Map();
  const startedAt = performance.now();
  let committed = 0;
  let sequence = 0;
  while ((performance.now() - startedAt) / 1000 < seconds) {
    const elapsedSeconds = (performance.now() - startedAt) / 1000;
    const targetCommitted = Math.floor(elapsedSeconds * rate);
    const batchSize = Math.min(1000, targetCommitted - committed);
    if (batchSize <= 0) {
      await sleep(10);
      continue;
    }
    const events = [];
    for (let i = 0; i < batchSize; i += 1) {
      const store = selectStore(sequence, stores);
      const logicalEventId = `${RUN_ID}-${name}-${sequence}`;
      events.push({ storeId: store.storeId, logicalEventId });
      if (!expectedByStore.has(store.storeId)) expectedByStore.set(store.storeId, new Set());
      expectedByStore.get(store.storeId).add(logicalEventId);
      sequence += 1;
    }
    const response = await commitLoadTestEvents(events);
    for (const event of events) {
      createdAtById.set(event.logicalEventId, response.outboxCreatedAtEpochMs);
    }
    committed += response.committed;
  }
  return { committed, expectedByStore, createdAtById };
}

async function commitLoadTestEvents(events) {
  if (events.length === 0) return { committed: 0 };
  return postJson("/__load-test/notification-metrics/events", { events });
}

async function waitForOutboxDrain(timeoutMs) {
  const deadlineAt = Date.now() + timeoutMs;
  let last = null;
  while (Date.now() < deadlineAt) {
    const postgres = await collectPostgres();
    last = postgres?.outbox;
    if (last?.pendingCount === 0) return last;
    await sleep(100);
  }
  throw new Error(`Outbox did not drain within ${timeoutMs}ms; pending=${last?.pendingCount}`);
}

async function waitForLogicalDelivery(connections, expectedByStore, timeoutMs) {
  const deadlineAt = Date.now() + timeoutMs;
  while (Date.now() < deadlineAt) {
    const missing = connections.some(({ state }) => {
      const expected = expectedByStore.get(state.storeId) || new Set();
      return [...expected].some((id) => !state.receivedLogicalEventIds.has(id));
    });
    if (!missing) return;
    await sleep(20);
  }
}

function captureConnectionCounters(connections) {
  return new Map(connections.map(({ state }) => [state, {
    duplicateDeliveries: state.duplicateDeliveries,
    streamOrderViolations: state.streamOrderViolations,
    serverDisconnected: state.serverDisconnected,
  }]));
}

function logicalDeliverySnapshot(connections, expectedByStore, before) {
  let healthyClientEventLoss = 0;
  let duplicateDeliveries = 0;
  let streamOrderViolations = 0;
  let healthyClientDisconnects = 0;
  for (const { state } of connections) {
    const previous = before.get(state) || {};
    if (!state.intentionallySlow) {
      const expected = expectedByStore.get(state.storeId) || new Set();
      healthyClientEventLoss += [...expected]
        .filter((id) => !state.receivedLogicalEventIds.has(id)).length;
      if (!previous.serverDisconnected && state.serverDisconnected) healthyClientDisconnects += 1;
    }
    duplicateDeliveries += state.duplicateDeliveries - (previous.duplicateDeliveries || 0);
    streamOrderViolations += state.streamOrderViolations - (previous.streamOrderViolations || 0);
  }
  return {
    healthyClientEventLoss,
    duplicateDeliveries,
    streamOrderViolations,
    healthyClientDisconnects,
  };
}

async function reconnectStormByReplayGap(fixture, sampler) {
  const steps = [];
  const recovery = [];

  for (const gap of RECONNECT_REPLAY_GAPS) {
    sampler.setPhase(`reconnect-storm-gap-${gap}`);
    const sseMetrics = new Metrics(`reconnect-gap-${gap}-sse-clients`);
    let connections = await openAllSseClients(fixture, sseMetrics);
    const cursorEvents = fixture.stores.map((store, index) => ({
      storeId: store.storeId,
      logicalEventId: `${RUN_ID}-reconnect-cursor-${gap}-${index}`,
    }));
    await commitLoadTestEvents(cursorEvents);
    await waitForOutboxDrain(30_000);
    const cursorExpected = new Map(
      cursorEvents.map((event) => [event.storeId, new Set([event.logicalEventId])]),
    );
    await waitForLogicalDelivery(connections, cursorExpected, 10_000);

    const statesByStore = new Map(
      fixture.stores.map((store) => [
        store.storeId,
        connections.filter(({ state }) => state.storeId === store.storeId).map(({ state }) => state),
      ])
    );
    const before = captureConnectionCounters(connections);
    await closeSseGroupAndWait(connections);

    const expectedByStore = new Map();
    const events = [];
    for (const store of fixture.stores) {
      const expected = new Set();
      for (let index = 0; index < gap; index += 1) {
        const logicalEventId = `${RUN_ID}-reconnect-gap-${gap}-${store.storeId}-${index}`;
        events.push({ storeId: store.storeId, logicalEventId });
        expected.add(logicalEventId);
      }
      expectedByStore.set(store.storeId, expected);
    }
    for (let offset = 0; offset < events.length; offset += 1000) {
      await commitLoadTestEvents(events.slice(offset, offset + 1000));
    }
    const drain = await waitForOutboxDrain(Math.max(30_000, events.length * 20));

    const reconnectStartedAtMs = Date.now();
    for (const states of statesByStore.values()) {
      for (const state of states) {
        state.reconnectStartedAtMs = reconnectStartedAtMs;
        state.firstActivityAfterReconnectAtMs = null;
        state.lastLogicalEventAtMs = null;
        state.reloadRequiredAtMs = null;
      }
    }
    const reopened = await Promise.all(
      fixture.stores.map((store) =>
        openSseGroup(
          store.storeId,
          fixture.hostUserId,
          POS_CONNECTIONS_PER_STORE,
          sseMetrics,
          statesByStore.get(store.storeId),
        )
      )
    );
    connections = reopened.flat();

    if (gap <= 300) {
      await waitForLogicalDelivery(connections, expectedByStore, 10_000);
    } else {
      await waitForReloadOrTimeout(connections, 10_000);
    }
    const snapshot = logicalDeliverySnapshot(connections, expectedByStore, before);
    const reconnectLatencies = connections.map(({ state }) => {
      const reachedAt = gap === 0
        ? state.firstActivityAfterReconnectAtMs
        : gap <= 300
          ? state.lastLogicalEventAtMs
          : state.reloadRequiredAtMs;
      return reachedAt ? reachedAt - reconnectStartedAtMs : null;
    }).filter(Number.isFinite);
    steps.push({
      label: `replay-gap-${gap}`,
      isolatedConnectionGroup: true,
      replayGapPerStore: gap,
      reconnectingConnections: connections.length,
      committedLogicalEvents: events.length,
      outboxPendingAfterStep: drain.pendingCount,
      ...snapshot,
      reloadRequiredCount: connections.reduce((sum, { state }) => sum + state.reloadRequiredCount, 0),
      sse: {
        errors: snapshot.healthyClientDisconnects,
        reconnectSyncP95Ms: percentile(reconnectLatencies, 95),
        reconnectSyncP99Ms: percentile(reconnectLatencies, 99),
      },
    });

    if (gap > 300) {
      const recoveryResult = await runRecoveryTraffic(fixture, connections);
      recovery.push({ label: `after-replay-gap-${gap}`, ...recoveryResult });
    }
    await closeSseGroupAndWait(connections);
  }

  return { steps, recovery };
}

async function waitForReloadOrTimeout(connections, timeoutMs) {
  const deadlineAt = Date.now() + timeoutMs;
  while (Date.now() < deadlineAt) {
    if (connections.every(({ state }) => state.reloadRequiredAtMs != null)) return;
    await sleep(20);
  }
}

async function runRecoveryTraffic(fixture, connections) {
  const metrics = new Metrics("recovery-traffic");
  const states = connections.map(({ state }) => state);
  await forEachLimited(states, 40, async (state) => {
    try {
      const changes = await timed(metrics, "recovery.changes", () =>
        getJson(
          `/api/pos/${state.storeId}/notifications/changes?since=${encodeURIComponent(state.lastEventId || "0-0")}`,
          fixture.hostUserId,
        )
      );
      await timed(metrics, "recovery.fullBaseline", () =>
        getJson(`/api/pos/${state.storeId}/hall/orders/pending-payments`, fixture.hostUserId)
      );
      if (changes.currentWatermark) state.lastEventId = changes.currentWatermark;
    } catch {
      // Metrics records the failed recovery request.
    }
  });
  return metrics.snapshot({
    recoveringConnections: states.length,
    successfulRecoveryClients: states.length - (metrics.status.get("5xx") || 0) - (metrics.status.get("4xx") || 0),
    domainStateConvergenceVerified: false,
  });
}

async function forEachLimited(items, concurrency, fn) {
  let index = 0;
  const workers = Array.from({ length: Math.min(concurrency, items.length) }, async () => {
    while (index < items.length) {
      const current = index;
      index += 1;
      await fn(items[current]);
    }
  });
  await Promise.all(workers);
}

async function posCommandUnderLoad(store, hostUserId) {
  console.log(`[pos-command-under-load] samples=${POS_COMMAND_SAMPLES}`);
  const metrics = new Metrics("pos-command-under-load");
  progress.phase("POS command under load", null, metrics);
  const sse = await openSseGroup(store.storeId, hostUserId, POS_CONNECTIONS_PER_STORE, metrics);

  try {
    for (let sample = 0; sample < POS_COMMAND_SAMPLES; sample += 1) {
      const customer = store.customers[sample % store.customers.length];
      const tableId = store.tableIds[sample % store.tableIds.length];
      const orderId = await createPendingOrder(
        store,
        tableId,
        store.menuIds[0],
        customer.userId,
      );

      await timed(metrics, "pos.command.confirm", () =>
        postWithoutBody(
          `/api/pos/${store.storeId}/hall/orders/${orderId}/confirm`,
          hostUserId,
        )
      );

      const kitchen = await getJson(
        `/api/pos/${store.storeId}/kitchen/active-items/by-ids?ids=${orderId}`,
        hostUserId,
      );
      const cookingItem = kitchen.items
        ?.find((order) => order.orderId === orderId)
        ?.items?.find((item) => item.requiresCooking && item.status === "COOKING");
      if (!cookingItem) throw new Error(`No COOKING item found for order ${orderId}`);

      await timed(metrics, "pos.command.cookComplete", () =>
        patchWithoutBody(
          `/api/pos/${store.storeId}/kitchen/order-items/${cookingItem.orderItemId}/complete`,
          hostUserId,
        )
      );

      const hall = await getJson(
        `/api/pos/${store.storeId}/hall/orders/by-ids?ids=${orderId}`,
        hostUserId,
      );
      const servingItem = hall.items
        ?.find((order) => order.orderId === orderId)
        ?.items?.find((item) => item.orderItemId === cookingItem.orderItemId && item.status === "COOKED");
      if (!servingItem) throw new Error(`No COOKED item found for order ${orderId}`);

      await timed(metrics, "pos.command.serve", () =>
        patchWithoutBody(
          `/api/pos/${store.storeId}/hall/orders/${orderId}/items/${servingItem.orderItemId}/serve`,
          hostUserId,
        )
      );

      await sleep(POS_COMMAND_ARRIVAL_INTERVAL_MS);
    }

    await sleep(500);
    return metrics.snapshot({ commandSamples: POS_COMMAND_SAMPLES });
  } finally {
    await closeSseGroupAndWait(sse);
    progress.phaseDone();
  }
}

async function createPendingOrder(store, tableId, menuId, userId) {
  await getJson(`/api/customer/tables/${tableId}`, userId);
  await postJson(
    `/api/customer/tables/${tableId}/cart/items/delta`,
    { menuId, delta: 1 },
    userId,
  );
  const snapshot = await getJson(`/api/customer/tables/${tableId}/cart/snapshot`, userId);
  return (
    await postJson(
      `/api/customer/tables/${tableId}/cart/commit`,
      { seenVersion: snapshot.meta.v, commitId: randomUUID() },
      userId,
    )
  ).orderId;
}

async function openSseGroup(storeId, hostUserId, count, metrics, resumeStates = []) {
  const group = [];
  for (let i = 0; i < count; i += 1) {
    const ticket = await timed(metrics, "sse.ticket", () =>
      getJson(`/api/pos/${storeId}/notifications/ticket`, hostUserId)
    );
    const controller = new AbortController();
    const connectionId = `${RUN_ID}-${storeId}-${i}-${randomUUID()}`;
    const state = resumeStates[i] || {
      clientSlot: i,
      storeId,
      lastEventId: null,
      syncTargetEventId: null,
      syncStartedAt: null,
      syncRecorded: false,
      lastActivityAtMs: Date.now(),
      maxSilenceMs: 0,
      activityCount: 0,
      streamOrderViolations: 0,
      receivedLogicalEventIds: new Set(),
      duplicateDeliveries: 0,
      reloadRequiredCount: 0,
      reloadRequiredAtMs: null,
      reconnectStartedAtMs: null,
      firstActivityAfterReconnectAtMs: null,
      lastLogicalEventAtMs: null,
      pauseReads: false,
      serverDisconnected: false,
      abortRequested: false,
    };
    state.storeId = storeId;
    state.connectionId = connectionId;
    state.abortRequested = false;
    state.serverDisconnected = false;
    const resumeQuery = state.lastEventId
      ? `&lastEventId=${encodeURIComponent(state.lastEventId)}`
      : "";
    const promise = readSse(
      `/api/pos/${storeId}/notifications/subscribe?ticket=${ticket.ticket}&connectionId=${connectionId}${resumeQuery}`,
      controller,
      metrics,
      state,
    );
    group.push({ controller, promise, state });
  }
  await sleep(250);
  return group;
}

async function openAllSseClients(fixture, metrics) {
  const groups = await Promise.all(
    fixture.stores.map((store) =>
      openSseGroup(store.storeId, fixture.hostUserId, POS_CONNECTIONS_PER_STORE, metrics)
    ),
  );
  const connections = groups.flat();
  await waitForServerConnectionCount(connections.length, 10_000);
  return connections;
}

async function closeSseGroup(group) {
  for (const conn of group) {
    conn.state.abortRequested = true;
    conn.controller.abort();
  }
  await Promise.allSettled(group.map((conn) => conn.promise));
}

async function closeSseGroupAndWait(group) {
  await closeSseGroup(group);
  await waitForServerConnectionCount(0, 10_000, true);
}

async function waitForServerConnectionCount(expected, timeoutMs, flushDisconnects = false) {
  const deadlineAt = Date.now() + timeoutMs;
  let actual = null;
  while (Date.now() < deadlineAt) {
    if (flushDisconnects) {
      await postJson("/__load-test/notification-metrics/flush-disconnects", {});
    }
    const runtime = await fetchJson(`${BASE_URL}/__load-test/notification-metrics`);
    actual = runtime.activeSseConnections;
    if (actual === expected) return;
    await sleep(100);
  }
  throw new Error(`Expected ${expected} server SSE connections, found ${actual}`);
}

async function readSse(path, controller, metrics, state) {
  try {
    const res = await fetch(`${BASE_URL}${path}`, { signal: controller.signal });
    if (!res.ok) throw new HttpError("sse.subscribe", res.status, await res.text());
    if (state.reconnectStartedAtMs && !state.firstActivityAfterReconnectAtMs) {
      state.firstActivityAfterReconnectAtMs = Date.now();
    }
    const reader = res.body.getReader();
    const decoder = new TextDecoder();
    let buffer = "";
    while (true) {
      if (state.pauseReads) {
        await sleep(100);
        continue;
      }
      const { done, value } = await reader.read();
      if (done) {
        if (!state.abortRequested) state.serverDisconnected = true;
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let idx;
      while ((idx = buffer.indexOf("\n\n")) >= 0) {
        const raw = buffer.slice(0, idx);
        buffer = buffer.slice(idx + 2);
        metrics.sseEvent(raw);
        const now = Date.now();
        state.maxSilenceMs = Math.max(state.maxSilenceMs, now - state.lastActivityAtMs);
        state.lastActivityAtMs = now;
        state.activityCount += 1;
        if (state.reconnectStartedAtMs && !state.firstActivityAfterReconnectAtMs) {
          state.firstActivityAfterReconnectAtMs = now;
        }
        const eventName = sseEventName(raw);
        if (eventName === "RELOAD_REQUIRED") {
          state.reloadRequiredCount += 1;
          state.reloadRequiredAtMs = now;
        }
        const logicalEventId = sseLogicalEventId(raw);
        if (logicalEventId) {
          state.lastLogicalEventAtMs = now;
          if (state.receivedLogicalEventIds.has(logicalEventId)) {
            state.duplicateDeliveries += 1;
          } else {
            state.receivedLogicalEventIds.add(logicalEventId);
          }
        }
        const eventId = sseEventId(raw);
        if (eventId && eventId !== "0-0") {
          if (state.lastEventId && compareStreamIds(eventId, state.lastEventId) <= 0) {
            state.streamOrderViolations += 1;
          }
          state.lastEventId = eventId;
          if (
            state.syncTargetEventId &&
            !state.syncRecorded &&
            compareStreamIds(eventId, state.syncTargetEventId) >= 0
          ) {
            state.syncRecorded = true;
            metrics.reconnectSync(performance.now() - state.syncStartedAt);
          }
        }
      }
    }
  } catch (error) {
    if (error.name !== "AbortError") {
      state.serverDisconnected = true;
      metrics.sseError(error);
    }
  }
}

function sseEventName(raw) {
  return raw.split("\n").find((line) => line.startsWith("event:"))
    ?.slice(6).trim() || null;
}

function sseLogicalEventId(raw) {
  const data = raw.split("\n")
    .filter((line) => line.startsWith("data:"))
    .map((line) => line.slice(5).trimStart())
    .join("\n");
  if (!data) return null;
  try {
    return JSON.parse(data)?.loadTestLogicalEventId || null;
  } catch {
    return null;
  }
}

function sseEventId(raw) {
  const idLine = raw.split("\n").find((line) => line.startsWith("id:"));
  return idLine?.slice(3).trim() || null;
}

function compareStreamIds(left, right) {
  const [leftMs, leftSeq] = left.split("-").map(Number);
  const [rightMs, rightSeq] = right.split("-").map(Number);
  if (leftMs !== rightMs) return leftMs - rightMs;
  return leftSeq - rightSeq;
}

async function timed(metrics, name, fn) {
  const start = performance.now();
  try {
    const value = await fn();
    metrics.ok(name, performance.now() - start);
    return value;
  } catch (error) {
    metrics.fail(name, performance.now() - start, error);
    throw error;
  }
}

async function getJson(path, userId) {
  const res = await fetch(`${BASE_URL}${path}`, { headers: headers(userId) });
  return parseResponse("GET " + path, res);
}

async function postJson(path, body, userId) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: headers(userId, true),
    body: JSON.stringify(body),
  });
  return parseResponse("POST " + path, res);
}

async function postWithoutBody(path, userId) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    headers: headers(userId),
  });
  return parseResponse("POST " + path, res);
}

async function patchWithoutBody(path, userId) {
  const res = await fetch(`${BASE_URL}${path}`, {
    method: "PATCH",
    headers: headers(userId),
  });
  return parseResponse("PATCH " + path, res);
}

async function parseResponse(name, res) {
  const text = await res.text();
  if (!res.ok) throw new HttpError(name, res.status, text);
  return text ? JSON.parse(text) : {};
}

function headers(userId, json = false) {
  const result = {};
  if (json) result["Content-Type"] = "application/json";
  if (userId) result["X-Load-Test-User-Id"] = userId;
  return result;
}

class Metrics {
  constructor(name) {
    this.name = name;
    this.latencies = new Map();
    this.status = new Map();
    this.errors = [];
    this.sse = {
      events: 0,
      errors: 0,
      errorMessages: [],
      reloadRequiredCount: 0,
      delaysMs: [],
      reconnectSyncDelaysMs: [],
    };
    this.startedAt = Date.now();
  }

  ok(name, ms) {
    this.addLatency(name, ms);
    this.addStatus("2xx");
  }

  fail(name, ms, error) {
    this.addLatency(name, ms);
    this.addStatus(error instanceof HttpError ? `${Math.floor(error.status / 100)}xx` : "client");
    this.error(error);
  }

  error(error) {
    if (this.errors.length < 20) {
      this.errors.push(String(error.message || error).slice(0, 400));
    }
  }

  sseError(error) {
    this.sse.errors += 1;
    if (this.sse.errorMessages.length < 20) {
      this.sse.errorMessages.push(String(error.message || error).slice(0, 400));
    }
    this.error(error);
  }

  sseEvent(raw) {
    this.sse.events += 1;
    const eventName = raw.split("\n").find((line) => line.startsWith("event:"))
      ?.slice(6).trim();
    if (eventName === "RELOAD_REQUIRED") {
      this.sse.reloadRequiredCount += 1;
      reloadRequiredClientEvents += 1;
    }

    const dataText = raw.split("\n")
      .filter((line) => line.startsWith("data:"))
      .map((line) => line.slice(5).trimStart())
      .join("\n");
    if (dataText) {
      try {
        const data = JSON.parse(dataText);
        if (data?.__outboxEventId) {
          deliveryReceipts.push({
            outboxEventId: String(data.__outboxEventId),
            logicalEventId: data.loadTestLogicalEventId || null,
            receivedAtMs: Date.now(),
          });
        }
      } catch {
        // connect and heartbeat payloads are not required to be JSON.
      }
    }
    const idLine = raw.split("\n").find((line) => line.startsWith("id:"));
    const idMs = idLine ? Number(idLine.slice(3).trim().split("-")[0]) : NaN;
    if (Number.isFinite(idMs)) this.sse.delaysMs.push(Date.now() - idMs);
  }

  reconnectSync(ms) {
    this.sse.reconnectSyncDelaysMs.push(ms);
  }

  addLatency(name, ms) {
    if (!this.latencies.has(name)) this.latencies.set(name, []);
    this.latencies.get(name).push(ms);
  }

  addStatus(key) {
    this.status.set(key, (this.status.get(key) || 0) + 1);
  }

  snapshot(extra = {}) {
    const all = [...this.latencies.values()].flat();
    const status = Object.fromEntries(this.status);
    const failedRequests = Object.entries(status)
      .filter(([key]) => key !== "2xx")
      .reduce((sum, [, count]) => sum + count, 0);
    return {
      ...extra,
      requests: all.length,
      status,
      errorRate: all.length ? failedRequests / all.length : 0,
      serverErrorRate: all.length ? (status["5xx"] || 0) / all.length : 0,
      p95Ms: percentile(all, 95),
      p99Ms: percentile(all, 99),
      byEndpoint: Object.fromEntries(
        [...this.latencies.entries()].map(([name, values]) => [
          name,
          { count: values.length, p95Ms: percentile(values, 95), p99Ms: percentile(values, 99) },
        ])
      ),
      sse: {
        events: this.sse.events,
        errors: this.sse.errors,
        errorMessages: this.sse.errorMessages,
        reloadRequiredCount: this.sse.reloadRequiredCount,
        delayP95Ms: percentile(this.sse.delaysMs, 95),
        delayP99Ms: percentile(this.sse.delaysMs, 99),
        reconnectSyncP95Ms: percentile(this.sse.reconnectSyncDelaysMs, 95),
        reconnectSyncP99Ms: percentile(this.sse.reconnectSyncDelaysMs, 99),
      },
      sampleErrors: this.errors,
    };
  }

  progressSnapshot() {
    const all = [...this.latencies.values()].flat();
    const elapsedSec = Math.max(1, Math.round((Date.now() - this.startedAt) / 1000));
    return {
      elapsedSec,
      requests: all.length,
      rps: Math.round((all.length / elapsedSec) * 10) / 10,
      p95Ms: percentile(all, 95),
      p99Ms: percentile(all, 99),
      status: Object.fromEntries(this.status),
      sseEvents: this.sse.events,
      sseErrors: this.sse.errors,
      errors: this.errors.length,
    };
  }
}

class HttpError extends Error {
  constructor(name, status, body) {
    super(`${name} failed with HTTP ${status}: ${String(body).slice(0, 240)}`);
    this.status = status;
  }
}

class ActuatorSampler {
  constructor(baseUrl, intervalMs) {
    this.baseUrl = baseUrl;
    this.intervalMs = intervalMs;
    this.samples = [];
    this.running = false;
    this.timer = null;
    this.phase = "setup";
    this.previousOutboxSample = null;
    this.metricNames = [
      "jvm.memory.used",
      "jvm.memory.max",
      "jvm.threads.live",
      "jvm.gc.pause",
      "process.cpu.usage",
      "system.cpu.usage",
      "process.files.open",
      "process.files.max",
      "hikaricp.connections",
      "hikaricp.connections.active",
      "hikaricp.connections.idle",
      "hikaricp.connections.pending",
      "hikaricp.connections.timeout",
      "http.server.requests",
    ];
  }

  start() {
    this.running = true;
    this.loop();
  }

  setPhase(phase) {
    this.phase = phase;
  }

  async stop() {
    this.running = false;
    if (this.timer) clearTimeout(this.timer);
    await this.collect("stop");
    return this.samples;
  }

  async loop() {
    while (this.running) {
      await this.collect("interval");
      await new Promise((resolve) => {
        this.timer = setTimeout(resolve, this.intervalMs);
      });
    }
  }

  async collect(reason) {
    const sample = {
      at: new Date().toISOString(),
      reason,
      phase: this.phase,
      metrics: {},
    };
    const metricResults = await Promise.allSettled(
      this.metricNames.map(async (name) => [name, await fetchMetric(this.baseUrl, name)])
    );
    for (const result of metricResults) {
      if (result.status === "fulfilled" && result.value[1]) {
        sample.metrics[result.value[0]] = result.value[1];
      }
    }
    sample.process = {
      rssBytes: process.memoryUsage().rss,
      heapUsedBytes: process.memoryUsage().heapUsed,
    };
    sample.external = {
      postgres: await collectPostgres(),
      redis: await collectRedis(),
    };
    sample.notificationRuntime = await fetchJson(`${this.baseUrl}/__load-test/notification-metrics`);
    const outbox = sample.external?.postgres?.outbox;
    if (outbox && Number.isFinite(outbox.publishedCount)) {
      const current = { atMs: Date.parse(sample.at), count: outbox.publishedCount };
      if (this.previousOutboxSample) {
        const elapsedSeconds = (current.atMs - this.previousOutboxSample.atMs) / 1000;
        outbox.relayEventsPerSecond = elapsedSeconds > 0
          ? (current.count - this.previousOutboxSample.count) / elapsedSeconds
          : 0;
      }
      this.previousOutboxSample = current;
    }
    this.samples.push(sample);
  }
}

class ProgressReporter {
  constructor(intervalMs) {
    this.intervalMs = intervalMs;
    this.current = null;
    this.timer = null;
    this.running = false;
  }

  start() {
    this.running = true;
    this.tick();
  }

  stop() {
    this.running = false;
    if (this.timer) clearTimeout(this.timer);
  }

  phase(name, plannedSeconds, metrics) {
    this.current = {
      name,
      plannedSeconds,
      metrics,
      startedAt: Date.now(),
    };
    this.print();
  }

  phaseDone() {
    this.print("done");
    this.current = null;
  }

  tick() {
    if (!this.running) return;
    this.print();
    this.timer = setTimeout(() => this.tick(), this.intervalMs);
  }

  print(status = "running") {
    if (!this.current) return;
    const elapsed = Math.round((Date.now() - this.current.startedAt) / 1000);
    const percent = this.current.plannedSeconds
      ? Math.min(100, Math.round((elapsed / this.current.plannedSeconds) * 100))
      : null;
    const snapshot = this.current.metrics.progressSnapshot();
    const progressText = percent == null
      ? `${elapsed}s`
      : `${elapsed}/${this.current.plannedSeconds}s ${percent}%`;
    console.log(
      `[progress] ${status} ${this.current.name} ${progressText} ` +
      `req=${snapshot.requests} rps=${snapshot.rps} p95=${snapshot.p95Ms ?? "-"}ms ` +
      `p99=${snapshot.p99Ms ?? "-"}ms 5xx=${snapshot.status["5xx"] ?? 0} ` +
      `sse=${snapshot.sseEvents}/${snapshot.sseErrors} errors=${snapshot.errors}`
    );
  }
}

async function collectPostgres() {
  if (POSTGRES_COLLECTOR === "off") return null;
  const activitySql = `
    select
      count(*) as connections,
      count(*) filter (where state = 'active') as active,
      count(*) filter (where wait_event is not null) as waiting,
      coalesce(max(extract(epoch from now() - query_start)) filter (where state = 'active'), 0)::bigint as max_active_seconds
    from pg_stat_activity
    where datname = current_database();
  `;
  const locksSql = `
    select count(*) as ungranted_locks
    from pg_locks
    where not granted;
  `;
  const statementsSql = `
    select
      calls,
      round(total_exec_time::numeric, 2) as total_exec_ms,
      round(mean_exec_time::numeric, 2) as mean_exec_ms,
      left(regexp_replace(query, '\\s+', ' ', 'g'), 160) as query
    from pg_stat_statements
    order by total_exec_time desc
    limit 5;
  `;
  const outboxSql = `
    select
      count(*) filter (where published_at is null) as pending_count,
      coalesce(max(extract(epoch from now() - created_at)) filter (where published_at is null), 0)::bigint as oldest_pending_age_seconds,
      count(*) filter (where published_at is not null) as published_count,
      count(*) filter (where attempts > 0) as failed_event_count,
      coalesce(sum(attempts), 0) as total_attempts,
      coalesce(percentile_cont(0.50) within group (order by extract(epoch from (published_at - created_at)) * 1000) filter (where published_at is not null), 0)::bigint as relay_p50_ms,
      coalesce(percentile_cont(0.95) within group (order by extract(epoch from (published_at - created_at)) * 1000) filter (where published_at is not null), 0)::bigint as relay_p95_ms,
      coalesce(percentile_cont(0.99) within group (order by extract(epoch from (published_at - created_at)) * 1000) filter (where published_at is not null), 0)::bigint as relay_p99_ms
    from pos_event_outbox;
  `;
  try {
    const [activity, locks, statements, outbox] = await Promise.all([
      runPsql(activitySql),
      runPsql(locksSql),
      runPsql(statementsSql, true),
      runPsql(outboxSql),
    ]);
    return {
      activity: parsePgRow(activity, ["connections", "active", "waiting", "maxActiveSeconds"]),
      locks: parsePgRow(locks, ["ungrantedLocks"]),
      slowQueries: parsePgStatements(statements),
      outbox: parsePgRow(outbox, [
        "pendingCount",
        "oldestPendingAgeSeconds",
        "publishedCount",
        "failedEventCount",
        "totalAttempts",
        "relayP50Ms",
        "relayP95Ms",
        "relayP99Ms",
      ]),
    };
  } catch (error) {
    return { error: String(error.message || error).slice(0, 300) };
  }
}

async function collectRedis() {
  if (REDIS_COLLECTOR === "off") return null;
  try {
    const [statsInfo, clientsInfo, latency] = await Promise.all([
      runRedis(["INFO", "stats"]),
      runRedis(["INFO", "clients"]),
      runRedis(["LATENCY", "LATEST"], true),
    ]);
    return {
      stats: parseRedisInfo(`${statsInfo}\n${clientsInfo}`),
      latencyLatest: latency.trim().split("\n").filter(Boolean).slice(0, 20),
    };
  } catch (error) {
    return { error: String(error.message || error).slice(0, 300) };
  }
}

async function runPsql(sql, allowFailure = false) {
  const args = POSTGRES_COLLECTOR === "docker"
    ? ["exec", "-i", POSTGRES_CONTAINER, "psql", "-U", POSTGRES_USER, "-d", POSTGRES_DB, "-At", "-F", ",", "-c", sql]
    : ["-U", POSTGRES_USER, "-d", POSTGRES_DB, "-At", "-F", ",", "-c", sql];
  const command = POSTGRES_COLLECTOR === "docker" ? "docker" : "psql";
  return runCommand(command, args, allowFailure);
}

async function runRedis(args, allowFailure = false) {
  const commandArgs = REDIS_COLLECTOR === "docker"
    ? ["exec", "-i", REDIS_CONTAINER, "redis-cli", ...args]
    : args;
  const command = REDIS_COLLECTOR === "docker" ? "docker" : "redis-cli";
  return runCommand(command, commandArgs, allowFailure);
}

async function runCommand(command, args, allowFailure = false) {
  try {
    const { stdout, stderr } = await execFileAsync(command, args, { timeout: 3000, maxBuffer: 1024 * 1024 });
    if (stderr && !allowFailure) return stdout;
    return stdout;
  } catch (error) {
    if (allowFailure) return "";
    throw error;
  }
}

async function runCommandCombined(command, args) {
  try {
    const { stdout, stderr } = await execFileAsync(command, args, {
      timeout: 5000,
      maxBuffer: 8 * 1024 * 1024,
    });
    return `${stdout}\n${stderr}`;
  } catch (error) {
    return `${error.stdout || ""}\n${error.stderr || ""}`;
  }
}

function parsePgRow(text, keys) {
  const parts = text.trim().split(",").filter((part) => part.length > 0);
  return Object.fromEntries(keys.map((key, index) => [key, numeric(parts[index])]));
}

function parsePgStatements(text) {
  return text.trim().split("\n").filter(Boolean).map((line) => {
    const [calls, totalExecMs, meanExecMs, ...queryParts] = line.split(",");
    return {
      calls: numeric(calls),
      totalExecMs: numeric(totalExecMs),
      meanExecMs: numeric(meanExecMs),
      query: queryParts.join(","),
    };
  });
}

function parseRedisInfo(text) {
  const wanted = new Set([
    "total_commands_processed",
    "instantaneous_ops_per_sec",
    "total_net_input_bytes",
    "total_net_output_bytes",
    "rejected_connections",
    "expired_keys",
    "evicted_keys",
    "keyspace_hits",
    "keyspace_misses",
    "total_error_replies",
    "connected_clients",
    "blocked_clients",
  ]);
  const result = {};
  for (const line of text.split("\n")) {
    const [key, value] = line.trim().split(":");
    if (wanted.has(key)) result[key] = numeric(value);
  }
  return result;
}

function numeric(value) {
  const number = Number(value);
  return Number.isFinite(number) ? number : value ?? "";
}

async function fetchMetric(baseUrl, name) {
  try {
    const res = await fetch(`${baseUrl}/actuator/metrics/${name}`);
    if (!res.ok) return null;
    const body = await res.json();
    return Object.fromEntries(body.measurements.map((m) => [m.statistic.toLowerCase(), m.value]));
  } catch {
    return null;
  }
}

async function fetchJson(url) {
  try {
    const res = await fetch(url);
    return res.ok ? await res.json() : { error: `HTTP ${res.status}` };
  } catch (error) {
    return { error: String(error.message || error).slice(0, 300) };
  }
}

async function collectRuntimeSignals(since) {
  const logs = await runCommandCombined(
    "docker",
    ["logs", "--since", since, APP_CONTAINER],
  );
  const loggedQueueOverflowConnectionIds = [...logs.matchAll(
    /\[SSE-DISCONNECT].*connectionId=([^\s]+).*reason=queue overflow/g,
  )].map((match) => match[1]);
  const runtime = await fetchJson(`${BASE_URL}/__load-test/notification-metrics`);
  const queueOverflowConnectionIds = [...new Set([
    ...loggedQueueOverflowConnectionIds,
    ...(runtime.faultInjectedQueueOverflowConnectionIds || []),
  ])];
  return {
    source: `docker logs ${APP_CONTAINER} plus loadtest runtime metrics`,
    reloadRequiredClientEvents,
    slowConsumerCount: countMatches(logs, /\[SSE-SLOW-CONSUMER]/g),
    disconnectCount: countMatches(logs, /\[SSE-DISCONNECT]/g),
    queueOverflowDisconnectCount: queueOverflowConnectionIds.length,
    queueOverflowConnectionIds,
    queueOverflowAffectedOnlyIntentionallySlowClients: queueOverflowConnectionIds.every((id) =>
      intentionalSlowConnectionIds.has(id)
    ),
    relayFailureLogCount: countMatches(logs, /Failed to publish POS outbox event/g),
  };
}

function countMatches(text, pattern) {
  return [...String(text || "").matchAll(pattern)].length;
}

async function writeArtifacts(result) {
  await mkdir(OUTPUT_DIR, { recursive: true });
  await writeFile(join(OUTPUT_DIR, "result.json"), JSON.stringify(result, null, 2));
  await writeFile(join(OUTPUT_DIR, "metrics.csv"), metricsCsv(result.samples));
  await writeFile(join(OUTPUT_DIR, "summary.md"), summaryMarkdown(result));
  console.log(`[load] artifacts written to ${OUTPUT_DIR}`);
}

function metricsCsv(samples) {
  const rows = [
    [
      "at",
      "rssBytes",
      "heapUsedBytes",
      "jvmMemoryUsed",
      "jvmMemoryMax",
      "jvmThreadsLive",
      "jvmGcPauseCount",
      "jvmGcPauseTotalSeconds",
      "jvmGcPauseMaxSeconds",
      "processCpuUsage",
      "systemCpuUsage",
      "processFilesOpen",
      "processFilesMax",
      "hikariActive",
      "hikariIdle",
      "hikariPending",
      "hikariTimeouts",
      "httpServerRequestsCount",
      "postgresConnections",
      "postgresActive",
      "postgresWaiting",
      "postgresUngrantedLocks",
      "outboxPendingCount",
      "outboxOldestPendingAgeSeconds",
      "outboxPublishedCount",
      "outboxFailedEventCount",
      "outboxRelayEventsPerSecond",
      "outboxRelayP95Ms",
      "activeSseConnections",
      "broadcastQueueDepthTotal",
      "outboundQueueDepthTotal",
      "catchUpQueueDepthTotal",
      "redisOpsPerSec",
      "redisTotalErrors",
      "redisRejectedConnections",
      "redisConnectedClients",
      "redisBlockedClients",
    ],
  ];
  for (const sample of samples) {
    rows.push([
      sample.at,
      sample.process?.rssBytes ?? "",
      sample.process?.heapUsedBytes ?? "",
      sample.metrics["jvm.memory.used"]?.value ?? "",
      sample.metrics["jvm.memory.max"]?.value ?? "",
      sample.metrics["jvm.threads.live"]?.value ?? "",
      sample.metrics["jvm.gc.pause"]?.count ?? "",
      sample.metrics["jvm.gc.pause"]?.total_time ?? "",
      sample.metrics["jvm.gc.pause"]?.max ?? "",
      sample.metrics["process.cpu.usage"]?.value ?? "",
      sample.metrics["system.cpu.usage"]?.value ?? "",
      sample.metrics["process.files.open"]?.value ?? "",
      sample.metrics["process.files.max"]?.value ?? "",
      sample.metrics["hikaricp.connections.active"]?.value ?? "",
      sample.metrics["hikaricp.connections.idle"]?.value ?? "",
      sample.metrics["hikaricp.connections.pending"]?.value ?? "",
      sample.metrics["hikaricp.connections.timeout"]?.count ?? "",
      sample.metrics["http.server.requests"]?.count ?? "",
      sample.external?.postgres?.activity?.connections ?? "",
      sample.external?.postgres?.activity?.active ?? "",
      sample.external?.postgres?.activity?.waiting ?? "",
      sample.external?.postgres?.locks?.ungrantedLocks ?? "",
      sample.external?.postgres?.outbox?.pendingCount ?? "",
      sample.external?.postgres?.outbox?.oldestPendingAgeSeconds ?? "",
      sample.external?.postgres?.outbox?.publishedCount ?? "",
      sample.external?.postgres?.outbox?.failedEventCount ?? "",
      sample.external?.postgres?.outbox?.relayEventsPerSecond ?? "",
      sample.external?.postgres?.outbox?.relayP95Ms ?? "",
      sample.notificationRuntime?.activeSseConnections ?? "",
      sample.notificationRuntime?.broadcastQueueDepth?.total ?? "",
      sample.notificationRuntime?.outboundQueueDepth?.total ?? "",
      sample.notificationRuntime?.catchUpQueueDepth?.total ?? "",
      sample.external?.redis?.stats?.instantaneous_ops_per_sec ?? "",
      sample.external?.redis?.stats?.total_error_replies ?? "",
      sample.external?.redis?.stats?.rejected_connections ?? "",
      sample.external?.redis?.stats?.connected_clients ?? "",
      sample.external?.redis?.stats?.blocked_clients ?? "",
    ]);
  }
  return rows.map((row) => row.map(csvCell).join(",")).join("\n") + "\n";
}

function assessPerformanceGuarantees(result) {
  const failures = [];
  const incomplete = [];
  const baseline = result.scenarios.idleConnectionBaseline || [];
  const plateau = baseline.find((row) => row.label === "plateau-hold") || baseline.at(-1);
  const unloaded = baseline[0];
  const expectedConnections = Math.min(
    Math.max(...BASELINE_STORE_STEPS),
    result.fixture?.stores || 0,
  ) * POS_CONNECTIONS_PER_STORE;

  if (plateau?.serverActiveConnections !== expectedConnections) {
    failures.push(
      `idleConnectionBaseline: active SSE ${plateau?.serverActiveConnections} != ${expectedConnections}`,
    );
  }
  if ((plateau?.maxHeartbeatGapMs ?? Infinity) > HEARTBEAT_DEADLINE_MS) {
    failures.push(
      `idleConnectionBaseline: heartbeat gap ${plateau?.maxHeartbeatGapMs}ms > ${HEARTBEAT_DEADLINE_MS}ms`,
    );
  }
  if ((plateau?.reloadRequiredCount || 0) > 0) {
    failures.push(`idleConnectionBaseline: RELOAD_REQUIRED=${plateau.reloadRequiredCount}`);
  }
  if ((plateau?.serverDisconnectedClients || 0) > 0) {
    failures.push(`idleConnectionBaseline: disconnected clients=${plateau.serverDisconnectedClients}`);
  }

  const baselineHikari = unloaded?.hikariActive ?? 0;
  const maxIdleHikari = maxMetric(baseline, (row) => row.hikariActive);
  if (Number.isFinite(maxIdleHikari) && maxIdleHikari > baselineHikari + 2) {
    failures.push(
      `idleConnectionBaseline: Hikari active grew from ${baselineHikari} to ${maxIdleHikari}`,
    );
  }

  const plateauSamples = result.samples.filter((sample) =>
    sample.phase === "idle-connection-baseline-plateau-hold"
  );
  const resourceTrend = {
    heap: continuousGrowth(plateauSamples, (sample) => sample.metrics?.["jvm.memory.used"]?.value, 32 * 1024 * 1024),
    threads: continuousGrowth(plateauSamples, (sample) => sample.metrics?.["jvm.threads.live"]?.value, 5),
    fileDescriptors: continuousGrowth(plateauSamples, (sample) => sample.metrics?.["process.files.open"]?.value, 20),
  };
  if (BASELINE_HOLD_SECONDS < 1800 || plateauSamples.length < 5) {
    incomplete.push(
      `resource leak hold is ${BASELINE_HOLD_SECONDS}s with ${plateauSamples.length} samples; needs at least 1800s`,
    );
  }
  if (expectedConnections < 400) {
    incomplete.push(`idle plateau has ${expectedConnections} connections; target proof needs 400`);
  }
  if (!STRESS_EVENT_RATES.includes(250)) {
    incomplete.push("stress curve does not include 250 events/s");
  }
  if (!RECONNECT_REPLAY_GAPS.some((gap) => gap > 300)) {
    incomplete.push("reconnect curve does not include a replay gap above 300");
  }
  incomplete.push("duplicate domain effect requires a real frontend reducer test");
  for (const [name, trend] of Object.entries(resourceTrend)) {
    if (trend.suspectedLeak) failures.push(`idleConnectionBaseline: ${name} continuously grows`);
  }

  const invariantRows = [
    ...(result.scenarios.evenTrafficStress || []),
    ...(result.scenarios.hotStoreTrafficStress || []),
    result.scenarios.slowClientIsolation,
    ...(result.scenarios.reconnectStormByReplayGap || []).filter((row) => row.replayGapPerStore <= 300),
  ].filter(Boolean);
  for (const row of invariantRows) {
    const label = row.label || "performance-step";
    if ((row.healthyClientEventLoss || 0) !== 0) {
      failures.push(`${label}: healthy client event loss=${row.healthyClientEventLoss}`);
    }
    if ((row.streamOrderViolations || 0) !== 0) {
      failures.push(`${label}: non-increasing Redis Stream IDs=${row.streamOrderViolations}`);
    }
    if ((row.healthyClientDisconnects || 0) !== 0) {
      failures.push(`${label}: healthy client disconnects=${row.healthyClientDisconnects}`);
    }
    if ((row.outboxPendingAfterStep || 0) !== 0) {
      failures.push(`${label}: outbox pending after step=${row.outboxPendingAfterStep}`);
    }
  }

  if (result.runtimeSignals?.queueOverflowAffectedOnlyIntentionallySlowClients === false) {
    failures.push("slowClientIsolation: queue overflow disconnected a healthy client");
  }
  const recoveryRows = result.scenarios.recoveryTraffic || [];
  for (const row of recoveryRows) {
    if ((row.errorRate || 0) > 0) failures.push(`${row.label}: recovery error rate=${row.errorRate}`);
    if (row.domainStateConvergenceVerified !== true) {
      incomplete.push(`${row.label}: recovery requests succeeded but domain-state convergence was not verified`);
    }
  }

  return {
    passed: failures.length === 0 && incomplete.length === 0,
    status: failures.length > 0 ? "FAIL" : incomplete.length > 0 ? "INCONCLUSIVE" : "PASS",
    failures,
    incomplete,
    expectedConnections,
    controlRestCurve: baseline.map((row) => ({
      stores: row.targetStores,
      connections: row.serverActiveConnections,
      errorRate: row.errorRate,
      p99Ms: row.p99Ms,
    })),
    resourceTrend,
  };
}

function continuousGrowth(samples, getter, minimumNetGrowth) {
  const values = samples.map(getter).filter(Number.isFinite);
  if (values.length < 5) {
    return { samples: values.length, suspectedLeak: false, reason: "needs at least 5 samples" };
  }
  let positiveSteps = 0;
  for (let index = 1; index < values.length; index += 1) {
    if (values[index] > values[index - 1]) positiveSteps += 1;
  }
  const netGrowth = values.at(-1) - values[0];
  const positiveRatio = positiveSteps / (values.length - 1);
  return {
    samples: values.length,
    first: values[0],
    last: values.at(-1),
    netGrowth,
    positiveRatio,
    suspectedLeak: netGrowth > minimumNetGrowth && positiveRatio >= 0.8,
  };
}

function csvCell(value) {
  const text = String(value ?? "");
  return /[",\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text;
}

function assessResults(result) {
  const scenarioRows = flattenScenarioRows(result.scenarios);
  const failures = [];
  for (const row of scenarioRows) {
    const label = row.label;
    if ((row.status?.["5xx"] || 0) > 0) failures.push(`${label}: 5xx=${row.status["5xx"]}`);
    if (row.p95Ms != null && row.p95Ms > 1000) failures.push(`${label}: p95 ${row.p95Ms}ms > 1000ms`);
    if (row.p99Ms != null && row.p99Ms > 3000) failures.push(`${label}: p99 ${row.p99Ms}ms > 3000ms`);
    if (row.sse?.errors > 0) failures.push(`${label}: SSE errors=${row.sse.errors}`);
    if (row.sse?.delayP95Ms != null && row.sse.delayP95Ms > 1000) failures.push(`${label}: SSE p95 delay ${row.sse.delayP95Ms}ms > 1000ms`);
    if (row.sse?.delayP99Ms != null && row.sse.delayP99Ms > 2000) failures.push(`${label}: SSE p99 delay ${row.sse.delayP99Ms}ms > 2000ms`);
    if (row.sse?.reconnectSyncP95Ms != null && row.sse.reconnectSyncP95Ms > 3000) failures.push(`${label}: reconnect sync p95 ${row.sse.reconnectSyncP95Ms}ms > 3000ms`);
    if (row.sse?.reconnectSyncP99Ms != null && row.sse.reconnectSyncP99Ms > 5000) failures.push(`${label}: reconnect sync p99 ${row.sse.reconnectSyncP99Ms}ms > 5000ms`);
    if (label.startsWith("posCommandUnderLoad")) {
      for (const [endpoint, latency] of Object.entries(row.byEndpoint || {})) {
        if (!endpoint.startsWith("pos.command.")) continue;
        if (latency.p95Ms != null && latency.p95Ms > 500) {
          failures.push(`${label}/${endpoint}: p95 ${latency.p95Ms}ms > 500ms`);
        }
        if (latency.p99Ms != null && latency.p99Ms > 1000) {
          failures.push(`${label}/${endpoint}: p99 ${latency.p99Ms}ms > 1000ms`);
        }
      }
    }
  }
  if (result.performanceGuarantees) {
    failures.push(...result.performanceGuarantees.failures);
  }
  return {
    passedMinimumGate: failures.length === 0,
    failures,
    firstLikelyBoundary: scenarioRows.find((row) =>
      (row.p95Ms != null && row.p95Ms > 1000) ||
      (row.p99Ms != null && row.p99Ms > 3000) ||
      (row.status?.["5xx"] || 0) > 0 ||
      (row.sse?.errors || 0) > 0
    )?.label ?? null,
    sampleCount: result.samples.length,
  };
}

function flattenScenarioRows(scenarios) {
  const rows = [];
  for (const [name, value] of Object.entries(scenarios)) {
    if (Array.isArray(value)) {
      value.forEach((row, index) => rows.push({
        ...row,
        label: row.label ? `${name}.${row.label}` : `${name}.step-${index + 1}`,
      }));
    } else if (value && typeof value === "object") {
      rows.push({ ...value, label: name });
    }
  }
  return rows;
}

function summaryMarkdown(result) {
  const rows = flattenScenarioRows(result.scenarios);
  const lines = [
    `# OurNight Load Test ${result.runId}`,
    "",
    `- Started: ${result.startedAt}`,
    `- Finished: ${result.finishedAt}`,
    `- Base URL: ${result.baseUrl}`,
    `- Scenario: ${result.environment.scenario}`,
    `- Fixture: ${result.fixture?.stores ?? 0} stores, ${result.fixture?.tables ?? 0} tables, ${result.fixture?.customers ?? 0} customers`,
    `- Minimum gate: ${result.assessment.passedMinimumGate ? "PASS" : "FAIL"}`,
    `- First likely boundary: ${result.assessment.firstLikelyBoundary ?? "not found"}`,
    "",
    "## Scenario Summary",
    "",
    "| Step | Requests | Error % | 5xx | p95 ms | p99 ms | SSE errors | SSE p95 delay ms | Reconnect p95 ms |",
    "| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
  ];
  for (const row of rows) {
    lines.push(`| ${row.label} | ${row.requests ?? ""} | ${((row.errorRate ?? 0) * 100).toFixed(2)} | ${row.status?.["5xx"] ?? 0} | ${row.p95Ms ?? ""} | ${row.p99Ms ?? ""} | ${row.sse?.errors ?? ""} | ${row.sse?.delayP95Ms ?? ""} | ${row.sse?.reconnectSyncP95Ms ?? ""} |`);
  }
  const peak = (getter) => maxMetric(result.samples, getter);
  lines.push(
    "",
    "## Notification Runtime Metrics",
    "",
    `- Active SSE connections peak: ${peak((sample) => sample.notificationRuntime?.activeSseConnections)}`,
    `- Broadcast/outbound/catch-up queue depth peak: ${peak((sample) => sample.notificationRuntime?.broadcastQueueDepth?.total)} / ${peak((sample) => sample.notificationRuntime?.outboundQueueDepth?.total)} / ${peak((sample) => sample.notificationRuntime?.catchUpQueueDepth?.total)}`,
    `- Outbox pending peak / oldest age: ${peak((sample) => sample.external?.postgres?.outbox?.pendingCount)} / ${peak((sample) => sample.external?.postgres?.outbox?.oldestPendingAgeSeconds)}s`,
    `- Relay events/s peak / failed events peak: ${peak((sample) => sample.external?.postgres?.outbox?.relayEventsPerSecond)} / ${peak((sample) => sample.external?.postgres?.outbox?.failedEventCount)}`,
    `- RELOAD_REQUIRED / slow-consumer / queue-overflow disconnect: ${result.runtimeSignals?.reloadRequiredClientEvents ?? 0} / ${result.runtimeSignals?.slowConsumerCount ?? 0} / ${result.runtimeSignals?.queueOverflowDisconnectCount ?? 0}`,
    `- File descriptors open peak: ${peak((sample) => sample.metrics?.["process.files.open"]?.value)}`,
    `- Redis connected clients peak: ${peak((sample) => sample.external?.redis?.stats?.connected_clients)}`,
  );
  if (result.performanceGuarantees) {
    const performanceRows = [
      ...(result.scenarios.evenTrafficStress || []),
      ...(result.scenarios.hotStoreTrafficStress || []),
      result.scenarios.slowClientIsolation,
      ...(result.scenarios.reconnectStormByReplayGap || []),
    ].filter(Boolean);
    lines.push(
      "",
      "## Performance Guarantees",
      "",
      `- Guarantee gate: ${result.performanceGuarantees.status}`,
      `- Expected idle plateau connections: ${result.performanceGuarantees.expectedConnections}`,
      `- Incomplete proof: ${result.performanceGuarantees.incomplete.length ? result.performanceGuarantees.incomplete.join("; ") : "none"}`,
      "",
      "| Step | Event loss | Stream order violations | Healthy disconnects |",
      "| --- | ---: | ---: | ---: |",
    );
    for (const row of performanceRows) {
      lines.push(
        `| ${row.label} | ${row.healthyClientEventLoss ?? ""} | ${row.streamOrderViolations ?? ""} | ${row.healthyClientDisconnects ?? ""} |`,
      );
    }
  }
  lines.push("", "## Failures", "");
  if (result.assessment.failures.length === 0) {
    lines.push("- None against the minimum automated gate.");
  } else {
    lines.push(...result.assessment.failures.map((failure) => `- ${failure}`));
  }
  lines.push("", "## Notes", "");
  lines.push(...result.notes.map((note) => `- ${note}`));
  lines.push("- Per-step commit-to-client delay uses outbox `created_at` as the closest externally observable commit-side timestamp.");
  lines.push("- Redis `LATENCY LATEST` is populated only when Redis latency monitoring records an event.");
  lines.push("- Postgres `pg_stat_statements` slow-query rows are included only when that extension is installed.");
  return lines.join("\n") + "\n";
}

function maxMetric(samples, getter) {
  const values = samples.map(getter).filter((value) => Number.isFinite(value));
  return values.length ? Math.max(...values) : "unavailable";
}

function percentile(values, p) {
  if (!values.length) return null;
  const sorted = [...values].sort((a, b) => a - b);
  return Math.round(sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)]);
}

function summarizeFixture(fixture) {
  return {
    stores: fixture.stores.length,
    tables: fixture.stores.reduce((sum, store) => sum + store.tableIds.length, 0),
    customers: fixture.stores.reduce((sum, store) => sum + store.customers.length, 0),
  };
}

function parseList(value) {
  return value.split(",").map((v) => Number(v.trim())).filter(Number.isFinite);
}

function intEnv(name, fallback) {
  return Number.parseInt(env(name, String(fallback)), 10);
}

function env(name, fallback) {
  return process.env[name] || fallback;
}
