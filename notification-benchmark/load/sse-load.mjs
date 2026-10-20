#!/usr/bin/env node
// SSE load generator for notification-benchmark — Run A / B / C.
//
// Producer (XADD to Redis) and S SSE clients live in one process, so
// latency = client receive time − producer timestamp uses one clock.
// No npm dependencies (node:http + a tiny RESP client).
//
// Run from notification-benchmark/ (production SLO, default port range):
//
//   ./gradlew :notification-reactive:bootJar && docker compose up -d --build
//
//   Target load (S=300, E=8/s/store) — does plain break the SLO?
//     node load/sse-load.mjs
//
//   Find the wall (ramp, keep going after a failure):
//     STEPS=60,120,300,600 CONTINUE_ON_FAIL=true node load/sse-load.mjs
//
//   Failure scenario (app restart → reconnect storm):
//     MODE=restart CLIENTS=300 node load/sse-load.mjs
//
//   MVC (notification-imperative, virtual threads) — same load, other container:
//     ./gradlew :notification-imperative:bootJar && docker compose up -d --build noti-imperative
//     OPTION=mvc-vt BASE_URL=http://127.0.0.1:18081 APP_CONTAINER=notification-benchmark-noti-imperative-1 node load/sse-load.mjs
//
// Each step prints a per-second timeline (conn/s, TIME_WAIT, failures, p99), so the
// order "ports run out → EADDRNOTAVAIL → SLO broken" is visible, not just the end state.
//
// Results: load/results/<RUN_ID>.json

import http from "node:http";
import net from "node:net";
import { randomUUID } from "node:crypto";
import { execFile } from "node:child_process";
import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { setTimeout as sleep } from "node:timers/promises";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

// ---------- config ----------
const env = (k, d) => process.env[k] ?? d;
const num = (k, d) => Number(env(k, d));
const list = (k, d) => env(k, d).split(",").map((x) => Number(x.trim())).filter((x) => x > 0);

const BASE_URL = env("BASE_URL", "http://127.0.0.1:18083");
const REDIS_HOST = env("REDIS_HOST", "127.0.0.1");
const REDIS_PORT = num("REDIS_PORT", 16379);
const APP_CONTAINER = env("APP_CONTAINER", "notification-benchmark-noti-reactive-1");
// which module's code the run measures (recorded with the git state in the result json)
const APP_MODULE = env("APP_MODULE", APP_CONTAINER.includes("imperative") ? "notification-imperative" : "notification-reactive");
const MODE = env("MODE", "ramp"); // ramp | restart
const STEPS = list("STEPS", "300");
const STEP_SECONDS = num("STEP_SECONDS", 60);
const WARMUP_SECONDS = num("WARMUP_SECONDS", 3);
const TABS_PER_STORE = num("TABS_PER_STORE", 6);
const E = num("EVENTS_PER_STORE_PER_SEC", 8);
const SLO_P99_MS = num("SLO_P99_MS", 1000);
const POLL_TIMEOUT_S = num("POLL_TIMEOUT_S", 2); // prediction only; StreamReceiver default is 2s
let PORT_RANGE_SIZE = num("PORT_RANGE_SIZE", 28232); // overwritten by the app container's real range when docker is available
const TIME_WAIT_SECONDS = num("TIME_WAIT_SECONDS", 60);
const CONTINUE_ON_FAIL = env("CONTINUE_ON_FAIL", "false") === "true";
const DRAIN_SECONDS = num("DRAIN_SECONDS", 3);
const CLIENTS = num("CLIENTS", 300); // restart mode
const RESTART_AT_SECONDS = num("RESTART_AT_SECONDS", 20);
const RECOVERY_SECONDS = num("RECOVERY_SECONDS", 60);
const RECONNECT_DELAY_MS = num("RECONNECT_DELAY_MS", 3000); // ≈ EventSource default retry
const RELOAD_DELAY_MS = num("RELOAD_DELAY_MS", 100); // RELOAD_REQUIRED → snapshot fetch → resubscribe
const RELOAD_LOOP_MAX = num("RELOAD_LOOP_MAX", 3); // more reloads than this for one client = loop → fail
// churn: during the step, CHURN_PER_SEC random clients drop their connection, stay offline CHURN_OFFLINE_MS,
// then reconnect with their own last received id (like a laptop sleep / Wi-Fi blip). Exercises the server's
// "Last-Event-ID → resume or RELOAD_REQUIRED" path, which never runs while every client stays connected.
const CHURN_PER_SEC = num("CHURN_PER_SEC", 0);
const CHURN_OFFLINE_MS = num("CHURN_OFFLINE_MS", 2000);
const TIMEWAIT_COLLECTOR = env("TIMEWAIT_COLLECTOR", "docker"); // docker | off
const LOG_COLLECTOR = env("LOG_COLLECTOR", "docker"); // docker | off
const STREAM_MAXLEN = num("STREAM_MAXLEN", 10000);
const KEEP_STREAMS = env("KEEP_STREAMS", "false") === "true"; // keep keys after the run for XRANGE
const OPTION = env("OPTION", "unlabeled"); // e.g. plain, store-broadcast — which design this run measures
const stamp = (d = new Date()) => {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`;
};
const RUN_ID = env("RUN_ID",
  `${OPTION}_${MODE}_S${MODE === "restart" ? CLIENTS : STEPS.join("-")}_E${E}_${stamp()}`);
const OUTPUT_DIR = env("OUTPUT_DIR", join("load", "results"));

// ---------- tiny RESP client ----------
class Redis {
  constructor(host, port) {
    this.q = [];
    this.buf = Buffer.alloc(0);
    this.sock = net.connect(port, host);
    this.sock.setNoDelay(true);
    this.ready = new Promise((res, rej) => {
      this.sock.once("connect", res);
      this.sock.once("error", rej);
    });
    this.sock.on("data", (d) => {
      this.buf = this.buf.length ? Buffer.concat([this.buf, d]) : d;
      this.drain();
    });
    this.sock.on("error", (e) => this.q.splice(0).forEach((p) => p.reject(e)));
  }
  cmd(...args) {
    let out = `*${args.length}\r\n`;
    for (const a of args) {
      const s = String(a);
      out += `$${Buffer.byteLength(s)}\r\n${s}\r\n`;
    }
    this.sock.write(out);
    return new Promise((resolve, reject) => this.q.push({ resolve, reject }));
  }
  drain() {
    while (this.q.length) {
      const r = parseResp(this.buf, 0);
      if (!r) return;
      this.buf = this.buf.subarray(r.end);
      const p = this.q.shift();
      r.value instanceof Error ? p.reject(r.value) : p.resolve(r.value);
    }
  }
  close() {
    this.sock.end();
  }
}

function parseResp(buf, i) {
  if (i >= buf.length) return null;
  const nl = buf.indexOf("\r\n", i);
  if (nl < 0) return null;
  const type = String.fromCharCode(buf[i]);
  const line = buf.toString("utf8", i + 1, nl);
  const next = nl + 2;
  switch (type) {
    case "+": return { value: line, end: next };
    case "-": return { value: new Error(line), end: next };
    case ":": return { value: Number(line), end: next };
    case "$": {
      const n = Number(line);
      if (n < 0) return { value: null, end: next };
      if (buf.length < next + n + 2) return null;
      return { value: buf.toString("utf8", next, next + n), end: next + n + 2 };
    }
    case "*": {
      const n = Number(line);
      if (n < 0) return { value: null, end: next };
      const arr = [];
      let j = next;
      for (let k = 0; k < n; k++) {
        const r = parseResp(buf, j);
        if (!r) return null;
        arr.push(r.value);
        j = r.end;
      }
      return { value: arr, end: j };
    }
    default:
      throw new Error("bad RESP type " + type);
  }
}

const infoField = (info, field) => Number(info.match(new RegExp(`^${field}:(\\d+)`, "m"))?.[1] ?? NaN);

// ---------- state ----------
const redis = new Redis(REDIS_HOST, REDIS_PORT);
const agent = new http.Agent({ keepAlive: false, maxSockets: Infinity });
const stores = [];
const clients = [];
let producing = false;
let reconnect = false;
let stopping = false;
let step = null; // current measurement window
const publishErrors = { count: 0, last: "" };
let baselineConnPerSec = 0;

function newStep(label, s) {
  return {
    label, clients: s, stores: Math.ceil(s / TABS_PER_STORE),
    latencies: [], reorder: 0, dup: 0, gaps: 0, disconnects: 0, subscribeFails: 0, reconnects: 0, reloads: 0,
    churns: 0,
    anomalies: [], measureFrom: null,
    samples: [], startedAt: Date.now(),
  };
}

// ---------- producer ----------
function ensureStore(idx) {
  while (stores.length <= idx) {
    const id = randomUUID();
    stores.push({
      id, key: `notification:store:${id}:pos`,
      sentSeq: 0, ackedSeq: 0, lastId: "0-0", carry: 0, pubTimes: new Map(),
    });
  }
  return stores[idx];
}

function publish(s) {
  const seq = ++s.sentSeq;
  const ts = Date.now();
  s.pubTimes.set(seq, ts);
  redis
    .cmd("XADD", s.key, "MAXLEN", "~", STREAM_MAXLEN, "*",
      "eventId", `${s.id}:${seq}`, "type", "LOADTEST",
      "payload", JSON.stringify({ seq }), "timestamp", ts)
    .then((id) => {
      // replies arrive in order on one connection → ackedSeq and lastId move together
      s.ackedSeq = seq;
      s.lastId = id;
    })
    .catch((e) => {
      publishErrors.count++;
      publishErrors.last = e.message;
    });
}

let lastTick = performance.now();
setInterval(() => {
  const now = performance.now();
  const dt = (now - lastTick) / 1000;
  lastTick = now;
  if (!producing || E <= 0) return;
  for (const s of stores) {
    s.carry += E * dt;
    while (s.carry >= 1) {
      s.carry -= 1;
      publish(s);
    }
  }
}, 50).unref();

// Redis stream ids "ms-seq" compare part by part
function cmpId(a, b) {
  if (!a || !b) return 0;
  const [am, as] = a.split("-").map(BigInt);
  const [bm, bs] = b.split("-").map(BigInt);
  return am !== bm ? (am > bm ? 1 : -1) : as !== bs ? (as > bs ? 1 : -1) : 0;
}

// ---------- SSE client ----------
function openClient(store) {
  const c = {
    // expected = first seq not yet received (contiguous prefix ends at expected-1)
    // ahead    = seqs received above that prefix; maxSeen = largest seq received
    store, expected: store.ackedSeq + 1, ahead: new Set(), maxSeen: store.ackedSeq, lastId: store.lastId,
    reloading: false, reloadCount: 0, reloadStreak: 0, churning: false, offline: false, catchUpBefore: 0,
    connected: false, closedByUs: false, req: null, everConnected: false,
  };
  clients.push(c);
  connect(c);
  return c;
}

function connect(c) {
  const url = new URL(`/api/pos/${c.store.id}/notifications`, BASE_URL);
  const req = http.request(url, {
    method: "POST", agent,
    headers: { Accept: "text/event-stream", "Last-Event-ID": c.lastId, "Content-Length": 0 },
  });
  c.req = req;
  let ended = false;
  let tcpUp = false;
  let status = null;
  // WebFlux commits SSE headers only on the first event, so an idle stream has no
  // response yet. "connected" therefore means: TCP to the app is up.
  req.on("socket", (sock) => {
    const up = () => {
      tcpUp = true;
      c.connected = true;
      c.everConnected = true;
    };
    if (sock.connecting) sock.once("connect", up);
    else up();
  });
  const onEnd = () => {
    if (ended) return;
    ended = true;
    c.connected = false;
    if (c.closedByUs || stopping) return;
    if (c.churning) {
      c.churning = false;
      c.offline = true;
      setTimeout(() => {
        if (stopping || c.closedByUs) return;
        c.offline = false;
        c.catchUpBefore = Date.now(); // events published before this are catch-up: latency = offline time, not SLO
        step && step.reconnects++;
        connect(c);
      }, CHURN_OFFLINE_MS);
      return;
    }
    if (c.reloading) {
      // client contract for RELOAD_REQUIRED: take a snapshot (= state as of now) and subscribe again from now
      c.reloading = false;
      step && step.reconnects++;
      setTimeout(() => !stopping && !c.closedByUs && connect(c), RELOAD_DELAY_MS); // ≈ snapshot fetch time
      return;
    }
    // subscribe fail: TCP never came up, or the app answered non-200 (e.g. 500 when the
    // first Redis read fails before any event). disconnect: an open stream ended.
    if (!tcpUp || (status != null && status !== 200)) step && step.subscribeFails++;
    else step && step.disconnects++;
    if (reconnect) {
      setTimeout(() => {
        if (stopping || c.closedByUs) return;
        step && step.reconnects++;
        connect(c);
      }, RECONNECT_DELAY_MS);
    }
  };
  req.on("response", (res) => {
    status = res.statusCode;
    if (res.statusCode !== 200) {
      res.resume();
      res.on("end", onEnd);
      res.on("error", onEnd);
      return;
    }
    res.setEncoding("utf8");
    let buf = "";
    res.on("data", (chunk) => {
      buf += chunk;
      let idx;
      while ((idx = buf.search(/\r?\n\r?\n/)) >= 0) {
        const block = buf.slice(0, idx);
        buf = buf.slice(idx).replace(/^\r?\n\r?\n/, "");
        onBlock(c, block);
      }
    });
    res.on("end", onEnd);
    res.on("aborted", onEnd);
    res.on("error", onEnd);
    res.on("close", onEnd);
  });
  req.on("error", onEnd);
  req.end();
}

function onBlock(c, block) {
  let id = null;
  const data = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith(":")) continue;
    const i = line.indexOf(":");
    const field = i < 0 ? line : line.slice(0, i);
    let value = i < 0 ? "" : line.slice(i + 1);
    if (value.startsWith(" ")) value = value.slice(1);
    if (field === "id") id = value;
    else if (field === "data") data.push(value);
  }
  if (!data.length) return;
  const now = Date.now();
  let ev, seq;
  try {
    ev = JSON.parse(data.join("\n"));
    if (ev.type === "RELOAD_REQUIRED") {
      onReload(c);
      return;
    }
    seq = JSON.parse(ev.payload).seq;
  } catch {
    return;
  }
  const prevId = c.lastId;
  if (id) c.lastId = id;
  if (step && ev.timestamp >= c.catchUpBefore) step.latencies.push(now - ev.timestamp);
  const isDup = seq < c.expected || c.ahead.has(seq);
  const isLate = !isDup && seq < c.maxSeen; // arrived after a newer event → ordering violation
  if (seq !== c.expected && step && step.anomalies.length < 50)
    step.anomalies.push({ t: now - step.startedAt, store: c.store.id, client: clients.indexOf(c),
      expected: c.expected, got: seq, kind: isDup ? "dup" : isLate ? "late" : "gap-open",
      prevSseId: prevId, sseId: id, latencyMs: now - ev.timestamp,
      // id-forward + the skipped seq shows up later (a dup)  → stream order ≠ seq order (producer side)
      // id-forward + never shows up                          → the app skipped an entry (real loss)
      // id-backward → the app sent an older entry after a newer one; id-same → sent the same entry twice
      idOrder: cmpId(id, prevId) > 0 ? "id-forward" : cmpId(id, prevId) < 0 ? "id-backward" : "id-same" });
  if (isDup) {
    if (step) step.dup++;
    return;
  }
  if (isLate && step) step.reorder++;
  if (seq === c.expected) {
    c.expected++;
    while (c.ahead.delete(c.expected)) c.expected++;
  } else {
    if (step && seq > c.maxSeen + 1) step.gaps++; // skipped past maxSeen → a new hole (filled later = reorder, never = lost)
    c.ahead.add(seq);
  }
  if (seq > c.maxSeen) c.maxSeen = seq;
}

function churnOne() {
  const pool = clients.filter((c) => c.connected && !c.reloading && !c.churning && !c.offline);
  if (!pool.length) return;
  const c = pool[Math.floor(Math.random() * pool.length)];
  c.churning = true;
  c.reloadStreak = 0;
  if (step) step.churns++;
  c.req?.destroy(); // keeps expected / lastId → reconnect resumes from what it really saw
}

// RELOAD_REQUIRED: the server says "your Last-Event-ID is too old". Emulated client:
// snapshot = everything published so far counts as seen → resume from the newest acked id.
function onReload(c) {
  if (step) step.reloads++;
  c.reloadCount++;
  c.reloadStreak++;
  c.reloading = true;
  c.expected = c.store.ackedSeq + 1;
  c.ahead.clear();
  c.maxSeen = c.store.ackedSeq;
  c.lastId = c.store.lastId;
  c.req?.destroy(); // onEnd → reconnect immediately (also fine if the server already ended the stream)
}

async function openClients(target) {
  const opened = [];
  while (clients.length < target) {
    const store = ensureStore(Math.floor(clients.length / TABS_PER_STORE));
    opened.push(openClient(store));
    if (opened.length % 50 === 0) await sleep(100);
  }
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline && opened.some((c) => !c.everConnected && !c.closedByUs)) await sleep(100);
  return opened.filter((c) => !c.everConnected).length;
}

// ---------- sampler ----------
let lastConnTotal = null;
let lastConnAt = 0;
let sampling = false;

async function countTimeWait() {
  if (TIMEWAIT_COLLECTOR !== "docker") return null;
  try {
    // 4th column `st`: 06 = TIME_WAIT; 3rd column = remote addr, :18EB = port 6379 → only app → Redis
    // sockets (they use ephemeral ports; inbound SSE sockets on :8083 do not). Count inside the container: at ~28k sockets the raw
    // /proc output is too big to ship through docker exec every second.
    const { stdout } = await execFileAsync("docker",
      ["exec", APP_CONTAINER, "sh", "-c",
        "cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | awk '$4==\"06\" && $3 ~ /:18EB$/{n++} END{print n+0}'"], { timeout: 5000 });
    const n = Number(stdout.trim());
    return Number.isFinite(n) ? n : null;
  } catch {
    return null;
  }
}

async function sample() {
  if (sampling) return;
  sampling = true;
  try {
    const now = Date.now();
    const [stats, cl, timeWait] = await Promise.all([
      redis.cmd("INFO", "stats"), redis.cmd("INFO", "clients"), countTimeWait(),
    ]);
    const total = infoField(stats, "total_connections_received");
    const connPerSec = lastConnTotal == null ? null : (total - lastConnTotal) / ((now - lastConnAt) / 1000);
    lastConnTotal = total;
    lastConnAt = now;
    let overdue = 0;
    for (const c of clients) {
      if (c.closedByUs || c.offline || c.churning) continue;
      const t = c.store.ackedSeq >= c.expected ? c.store.pubTimes.get(c.expected) : undefined;
      if (t !== undefined && now - t > SLO_P99_MS) overdue++;
    }
    for (const s of stores) {
      const floor = s.sentSeq - Math.max(STREAM_MAXLEN, E * 300);
      for (const k of s.pubTimes.keys()) {
        if (k >= floor) break;
        s.pubTimes.delete(k);
      }
    }
    const sampleRow = {
      t: now, connPerSec, redisClients: infoField(cl, "connected_clients"), timeWait, overdue,
      // planned states (offline after churn, reconnecting after reload) count as alive
      connected: clients.filter((c) => c.connected || c.offline || c.churning || c.reloading).length,
      // cumulative counters of the current step → per-second timeline
      disc: step?.disconnects ?? 0, sub: step?.subscribeFails ?? 0, latN: step?.latencies.length ?? 0,
    };
    if (step) step.samples.push(sampleRow);
  } catch (e) {
    if (step) step.samples.push({ t: Date.now(), error: e.message });
  } finally {
    sampling = false;
  }
}
const sampler = setInterval(sample, 1000);
sampler.unref();

// ---------- summary ----------
function pct(sorted, p) {
  if (!sorted.length) return null;
  return sorted[Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1)];
}

function summarize(st) {
  const lat = st.latencies.slice().sort((a, b) => a - b);
  const rows = st.samples.filter((r) => !r.error);
  const conn = rows.map((r) => r.connPerSec).filter((x) => x != null);
  const tw = rows.map((r) => r.timeWait).filter((x) => x != null);
  const avgRaw = conn.length ? conn.reduce((a, b) => a + b, 0) / conn.length : null;
  const avgConn = avgRaw == null ? null : Math.max(0, avgRaw - baselineConnPerSec); // app's own conn/s
  const sum = {
    label: st.label, clients: st.clients, stores: st.stores, seconds: Math.round((Date.now() - st.startedAt) / 1000),
    events: lat.length, p50: pct(lat, 50), p99: pct(lat, 99), max: lat.at(-1) ?? null,
    reorder: st.reorder, dup: st.dup, gaps: st.gaps, overdueMax: Math.max(0, ...rows.map((r) => r.overdue)),
    reloads: st.reloads, // reloadMaxPerClient = longest run of reloads with no planned churn in between
    reloadMaxPerClient: Math.max(0, ...clients.map((c) => c.reloadStreak)),
    churns: st.churns,
    disconnects: st.disconnects, subscribeFails: st.subscribeFails, reconnects: st.reconnects,
    aliveMin: Math.min(st.clients, ...rows.filter((r) => !st.measureFrom || r.t >= st.measureFrom).map((r) => r.connected)),
    connPerSecAvg: avgConn, rPerSubscription: avgConn == null ? null : avgConn / st.clients,
    connPerSecPredictedIdle: st.clients / POLL_TIMEOUT_S,
    timeWaitMax: tw.length ? Math.max(...tw) : null, portRangeSize: PORT_RANGE_SIZE,
  };
  const t0 = st.startedAt;
  const exhausted = rows.find((r) => r.timeWait != null && r.timeWait >= 0.95 * PORT_RANGE_SIZE);
  const firstFail = rows.find((r) => r.disc + r.sub > 0 || r.overdue > 0);
  sum.portsExhaustedAtS = exhausted ? Math.round((exhausted.t - t0) / 1000) : null;
  const alive = rows.filter((r) => r.connPerSec != null && (!firstFail || r.t < firstFail.t));
  const aliveConn = alive.length ? Math.max(0, alive.reduce((a, r) => a + r.connPerSec, 0) / alive.length - baselineConnPerSec) : null;
  sum.connPerSecBeforeFailure = aliveConn;
  sum.rBeforeFailure = aliveConn == null ? null : aliveConn / st.clients;
  sum.firstFailureAtS = firstFail ? Math.round((firstFail.t - t0) / 1000) : null;
  let prevN = 0;
  sum.timeline = rows.map((r) => {
    const sec = st.latencies.slice(prevN, r.latN).sort((a, b) => a - b);
    prevN = r.latN;
    return {
      t: Math.round((r.t - t0) / 1000), connPerSec: r.connPerSec, timeWait: r.timeWait,
      fails: r.disc + r.sub, overdue: r.overdue, p99: pct(sec, 99),
    };
  });
  sum.secondsOverSlo = sum.timeline.filter((x) => x.p99 != null && x.p99 >= SLO_P99_MS).map((x) => x.t);
  sum.anomalies = st.anomalies;
  sum.fail = [
    sum.p99 != null && sum.p99 >= SLO_P99_MS && `p99 ${sum.p99}ms ≥ ${SLO_P99_MS}`,
    sum.reorder > 0 && `ordering violations ${sum.reorder}`,
    E > 0 && sum.events === 0 && "no events delivered",
    sum.reloadMaxPerClient > RELOAD_LOOP_MAX && `reload loop (max ${sum.reloadMaxPerClient}/client)`,
    sum.overdueMax > 0 && `overdue ${sum.overdueMax}`,
    sum.disconnects > 0 && `disconnects ${sum.disconnects}`,
    sum.subscribeFails > 0 && `subscribeFails ${sum.subscribeFails}`,
  ].filter(Boolean);
  return sum;
}

const f = (x, d = 0) => (x == null ? "-" : Number(x).toFixed(d));
const at = (sec) => (sec == null ? "never" : `+${sec}s`);
function printStep(s) {
  console.log(
    `S=${String(s.clients).padStart(4)} M=${String(s.stores).padStart(3)} | ` +
    `p50 ${f(s.p50)}ms p99 ${f(s.p99)}ms n=${s.events} | ` +
    `reorder ${s.reorder} dup ${s.dup} gaps ${s.gaps} overdue ${s.overdueMax} reload ${s.reloads} (max/client ${s.reloadMaxPerClient}) churn ${s.churns} | ` +
    `disc ${s.disconnects} subFail ${s.subscribeFails} alive min ${s.aliveMin}/${s.clients} | ` +
    `conn/s ${f(s.connPerSecAvg, 1)} (r=${f(s.rPerSubscription, 2)}/s, idle pred ${f(s.connPerSecPredictedIdle, 1)}) | ` +
    `TIME_WAIT max ${f(s.timeWaitMax)}/${s.portRangeSize} | ` +
    `seconds p99≥SLO [${s.secondsOverSlo.join(",")}] | ` +
    (s.fail.length ? `FAIL: ${s.fail.join(", ")}` : "PASS"),
  );
  if (s.portsExhaustedAtS != null || s.firstFailureAtS != null)
    console.log(`  ports ≥95% TIME_WAIT at ${at(s.portsExhaustedAtS)}, first failure at ${at(s.firstFailureAtS)}, ` +
      `conn/s before failure ${f(s.connPerSecBeforeFailure)} (r=${f(s.rBeforeFailure, 2)}/s)`);
  if (s.anomalies?.length)
    console.log(`  order anomalies (first ${s.anomalies.length}): ` +
      s.anomalies.slice(0, 5).map((a) => `t+${a.t}ms ${a.store.slice(0, 8)} c${a.client} expected ${a.expected} got ${a.got} ${a.kind} ${a.idOrder}`).join("; "));
  if (s.timeline?.length)
    console.log("  timeline t:conn/s|TIME_WAIT|fails|overdue|p99ms  " +
      s.timeline.map((x) => `${x.t}:${f(x.connPerSec)}|${f(x.timeWait)}|${x.fails}|${x.overdue}|${f(x.p99)}`).join("  "));
}

async function grepAppLogs(since) {
  if (LOG_COLLECTOR !== "docker") return null;
  try {
    const { stdout, stderr } = await execFileAsync("docker",
      ["logs", "--since", since, APP_CONTAINER], { timeout: 10000, maxBuffer: 64 * 1024 * 1024 });
    const text = stdout + stderr;
    return {
      eaddrnotavail: (text.match(/Cannot assign requested address|EADDRNOTAVAIL/g) || []).length,
      poolExhausted: (text.match(/Pool exhausted|NoSuchElementException/g) || []).length,
      errors: (text.match(/\bERROR\b/g) || []).length,
    };
  } catch (e) {
    return { error: e.message };
  }
}

// ---------- modes ----------
async function runRamp() {
  const results = [];
  for (const s of STEPS) {
    step = newStep(`S=${s}`, s); // open + warmup are inside the step: early failures count
    const notConnected = await openClients(s);
    await sleep(WARMUP_SECONDS * 1000);
    step.subscribeFails += notConnected;
    step.measureFrom = Date.now();
    const churner = CHURN_PER_SEC > 0 ? setInterval(churnOne, 1000 / CHURN_PER_SEC) : null;
    await sleep(STEP_SECONDS * 1000);
    if (churner) clearInterval(churner);
    await sleep(CHURN_PER_SEC > 0 ? CHURN_OFFLINE_MS + 1000 : 0); // let the last churned clients come back
    const sum = summarize(step);
    results.push(sum);
    printStep(sum);
    if (sum.fail.length && !CONTINUE_ON_FAIL) {
      console.log(`→ wall at S=${s} (stopping; CONTINUE_ON_FAIL=true to keep going)`);
      break;
    }
  }
  return results;
}

async function runRestart() {
  reconnect = true;
  step = newStep("before-restart", CLIENTS);
  await openClients(CLIENTS);
  await sleep(WARMUP_SECONDS * 1000);
  step.measureFrom = Date.now();
  await sleep(RESTART_AT_SECONDS * 1000);
  const before = summarize(step);
  printStep(before);

  step = newStep("recovery", CLIENTS);
  const restartAt = Date.now();
  console.log(`docker restart ${APP_CONTAINER} …`);
  execFileAsync("docker", ["restart", APP_CONTAINER]).catch((e) => console.log("restart failed:", e.message));
  const timeline = [];
  let allBackAt = null;
  let sloBackAt = null;
  let lastLat = 0;
  for (let i = 0; i < RECOVERY_SECONDS; i++) {
    await sleep(1000);
    const connected = clients.filter((c) => c.connected).length;
    const secLat = step.latencies.slice(lastLat).sort((a, b) => a - b);
    lastLat = step.latencies.length;
    const p99 = pct(secLat, 99);
    timeline.push({ t: i + 1, connected, p99, events: secLat.length });
    if (i > 2 && allBackAt == null && connected === CLIENTS) allBackAt = Date.now();
    if (allBackAt != null && sloBackAt == null && p99 != null && p99 < SLO_P99_MS) sloBackAt = Date.now();
  }
  const recovery = summarize(step);
  printStep(recovery);
  const rt = {
    allReconnectedAfterS: allBackAt && (allBackAt - restartAt) / 1000,
    sloBackAfterS: sloBackAt && (sloBackAt - restartAt) / 1000,
  };
  console.log(`all reconnected after ${f(rt.allReconnectedAfterS, 1)}s, p99 < SLO again after ${f(rt.sloBackAfterS, 1)}s`);
  console.log("timeline (s: connected / p99ms):",
    timeline.map((x) => `${x.t}:${x.connected}/${f(x.p99)}`).join(" "));
  return [before, { ...recovery, ...rt, timeline }];
}

async function gitInfo() {
  try {
    const sha = (await execFileAsync("git", ["rev-parse", "--short", "HEAD"])).stdout.trim();
    const dirty = (await execFileAsync("git", ["status", "--porcelain", "--", `../${APP_MODULE}`])).stdout.trim() !== "";
    return { commit: sha, module: APP_MODULE, dirty };
  } catch {
    return null;
  }
}

// ---------- main ----------
async function main() {
  await redis.ready;
  const since = new Date().toISOString();
  if (TIMEWAIT_COLLECTOR === "docker") {
    try {
      const { stdout } = await execFileAsync("docker",
        ["exec", APP_CONTAINER, "cat", "/proc/sys/net/ipv4/ip_local_port_range"], { timeout: 3000 });
      const [lo, hi] = stdout.trim().split(/\s+/).map(Number);
      if (process.env.PORT_RANGE_SIZE && Number(process.env.PORT_RANGE_SIZE) !== hi - lo + 1)
        console.log(`WARNING: PORT_RANGE_SIZE=${process.env.PORT_RANGE_SIZE} but the app container really has ${hi - lo + 1}`);
      PORT_RANGE_SIZE = hi - lo + 1;
      console.log(`app port range: ${lo}-${hi} (${PORT_RANGE_SIZE} ports)`);
    } catch (e) {
      console.log(`could not read app port range (${e.message.split("\n")[0]}); using ${PORT_RANGE_SIZE}`);
    }
  }
  {
    const a = infoField(await redis.cmd("INFO", "stats"), "total_connections_received");
    await sleep(3000);
    const b = infoField(await redis.cmd("INFO", "stats"), "total_connections_received");
    baselineConnPerSec = (b - a) / 3;
    console.log(`redis background conn/s before load: ${f(baselineConnPerSec, 2)} (healthcheck etc.; subtracted from conn/s)`);
  }
  const budget = PORT_RANGE_SIZE / TIME_WAIT_SECONDS;
  console.log(`run ${RUN_ID} option=${OPTION} mode=${MODE} base=${BASE_URL} E=${E}/s/store tabs=${TABS_PER_STORE} SLO p99<${SLO_P99_MS}ms`);
  console.log(`PORT BUDGET: ${PORT_RANGE_SIZE} ports / ${TIME_WAIT_SECONDS}s TIME_WAIT ≈ ${f(budget)} new conn/s`);
  const prediction = (MODE === "restart" ? [CLIENTS] : STEPS).map((s) => {
    const lo = s / POLL_TIMEOUT_S;
    const hi = s * Math.max(E, 1 / POLL_TIMEOUT_S);
    const runsOutS = hi > budget ? PORT_RANGE_SIZE / hi : null;
    console.log(`PREDICTION S=${s} (plain): conn/s between ${f(lo)} (r=1/T) and ${f(hi)} (r≈E)` +
      (runsOutS ? ` → if r≈E, ports run out ≈ ${f(runsOutS)}s after load starts → EADDRNOTAVAIL → SLO fail` : " → within budget"));
    return { clients: s, connPerSecLow: lo, connPerSecHigh: hi, portsRunOutS: runsOutS };
  });
  producing = true;
  let results;
  try {
    results = MODE === "restart" ? await runRestart() : await runRamp();
  } finally {
    producing = false;
    await sleep(DRAIN_SECONDS * 1000);
    // lost = published (acked) but never received by a client that is still connected, after the drain
    let lost = 0;
    for (const c of clients) {
      if (!c.connected || c.store.ackedSeq < c.expected) continue;
      let have = 0;
      for (const a of c.ahead) if (a <= c.store.ackedSeq) have++;
      lost += c.store.ackedSeq - c.expected + 1 - have;
    }
    stopping = true;
    for (const c of clients) {
      c.closedByUs = true;
      c.req?.destroy();
    }
    const logs = await grepAppLogs(since);
    if (logs) console.log("app logs:", JSON.stringify(logs));
    console.log(lost ? `LOST: ${lost} events published but never received by still-connected clients → SLO fail`
      : "lost: 0 (every published event reached every still-connected client)");
    if (publishErrors.count) console.log(`publish errors: ${publishErrors.count} (${publishErrors.last})`);
    await mkdir(OUTPUT_DIR, { recursive: true });
    const file = join(OUTPUT_DIR, `${RUN_ID}.json`);
    await writeFile(file, JSON.stringify({
      runId: RUN_ID, option: OPTION, code: await gitInfo(), mode: MODE, config: {
        BASE_URL, E, TABS_PER_STORE, baselineConnPerSec, CHURN_PER_SEC, CHURN_OFFLINE_MS, STEPS, STEP_SECONDS, SLO_P99_MS, POLL_TIMEOUT_S,
        PORT_RANGE_SIZE, TIME_WAIT_SECONDS, CLIENTS, RESTART_AT_SECONDS, RECOVERY_SECONDS,
      },
      prediction: { portBudgetPerSec: budget, steps: prediction }, results, lost, appLogs: logs, publishErrors,
    }, null, 2));
    console.log(`→ ${file}`);
    if (KEEP_STREAMS) console.log(`streams kept (${stores.length} keys: notification:store:<id>:pos)`);
    for (let i = 0; !KEEP_STREAMS && i < stores.length; i += 100) {
      await redis.cmd("DEL", ...stores.slice(i, i + 100).map((s) => s.key)).catch(() => {});
    }
    redis.close();
    agent.destroy();
  }
}

main().then(() => process.exit(0), (e) => {
  console.error(e);
  process.exit(1);
});
