#!/usr/bin/env bash
# MVC per-store hub (C) behavior check — step 2 and step 3.
#   B: two subscribers on one store both get the same event            (step 2)
#   C: last subscriber leaves -> channel closes about 30s later         (step 3)
#   R: after the close, a new subscriber gets a fresh, working channel  (step 3)
#   Q: subscriber returns within 30s -> channel does NOT close          (step 3)
#   P: Last-Event-ID inside the buffer -> only the missed events, then live (step 4)
#   N: no Last-Event-ID -> nothing old, only events from now on           (step 4)
#   O: Last-Event-ID older than the channel start -> one RELOAD, closed   (step 4)
#   X: malformed Last-Event-ID -> one RELOAD, closed                      (step 4)
#   Z: poison record (no "type") kills the reader -> channel cleaned up,
#      subscribers completed, a new subscriber gets a fresh channel     (step 6)
#   S: app shutdown (SIGTERM) -> loops end, executor terminates, curls end (step 6, runs last)
#
# The server only notices a closed curl when a send to it fails, so after
# killing a curl this script XADDs until the app logs "remove +".
#
# This script starts the app itself (from the boot jar) so it can read the
# app log and stop the app cleanly. Stop any bootRun on 8081 first.
#
# Run (from notification-benchmark/):
#   docker compose up -d redis
#   ./load/hub-check.sh            # about 3 minutes
# Env: JAVA=/path/to/java (default: ~/.sdkman/candidates/java/25*, then java_home -v 25, then java on PATH)
set -u
cd "$(dirname "$0")/.."

BASE=${BASE_URL:-http://localhost:8081}
# find a Java 25 (the jar is compiled for 25; plain `java` on PATH may be older)
is25() { "$1" -version 2>&1 | head -1 | grep -q '"25'; }
find_java() {
  local j
  for j in "$HOME"/.sdkman/candidates/java/25*/bin/java; do [ -x "$j" ] && is25 "$j" && { echo "$j"; return; }; done
  j=$(/usr/libexec/java_home -v 25 2>/dev/null)/bin/java && [ -x "$j" ] && is25 "$j" && { echo "$j"; return; }
  echo java
}
JAVA=${JAVA:-$(find_java)}
JAR=../notification-imperative/build/libs/noti-mvc.jar
TMP=$(mktemp -d)
LOG="$TMP/app.log"
APP_PID=""
PIDS=()
pass=0; fail=0; warn=0

cleanup() {
  for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null; done
  if [ -n "$APP_PID" ]; then kill "$APP_PID" 2>/dev/null; wait "$APP_PID" 2>/dev/null; fi
  echo "app log: $LOG"
}
trap cleanup EXIT

key()  { echo "notification:store:$1:pos"; }
rc()   { docker compose exec -T redis redis-cli "$@"; }
xadd() { rc XADD "$(key "$1")" '*' eventId "$2" type TEST payload '{}' timestamp "$(date +%s)000" >/dev/null; }
# xid STORE EVENTID -> XADD and print the Redis id
xid()  { rc XADD "$(key "$1")" '*' eventId "$2" type TEST payload '{}' timestamp "$(date +%s)000" | tr -d '"\r'; }
# sub STORE FILE [LAST_EVENT_ID] -> curl in background, sets PID
sub()  {
  if [ -n "${3:-}" ]; then curl -sN -X POST -H "Last-Event-ID: $3" "$BASE/api/pos/$1/notifications" >"$2" 2>&1 &
  else curl -sN -X POST "$BASE/api/pos/$1/notifications" >"$2" 2>&1 & fi
  PID=$!; PIDS+=("$PID")
}
# reloads FILE -> how many RELOAD_REQUIRED events the curl got
reloads(){ grep -c '"type":"RELOAD_REQUIRED"' "$1" 2>/dev/null || true; }
# closed PID -> true if that curl has exited (server completed the response)
closed() { ! kill -0 "$1" 2>/dev/null; }
# eventIds received, comma-separated, in arrival order
got()  { grep '^data:' "$1" 2>/dev/null | sed -E 's/.*"eventId":"([^"]*)".*/\1/' | paste -sd, -; }
# how many app-log lines match PATTERN
count(){ grep -c -- "$1" "$LOG" 2>/dev/null || true; }
ok()   { echo "  PASS $1"; pass=$((pass+1)); }
bad()  { echo "  FAIL $1"; fail=$((fail+1)); }
note() { echo "  WARN $1"; warn=$((warn+1)); }
check(){ if [ "$2" = "$3" ]; then ok "$1  (got: ${3:-<none>})"; else bad "$1  want: ${2:-<none>}  got: ${3:-<none>}"; fi; }

# wait_for PATTERN N SECS -> waits until PATTERN is in the app log N times; prints seconds waited, or returns 1
wait_for() {
  local s=0
  while [ "$(count "$1")" -lt "$2" ]; do
    [ "$s" -ge "$3" ] && return 1
    sleep 1; s=$((s+1))
  done
  echo "$s"
}

# drop STORE CURL_PID -> kill the curl, then XADD until the app logs a failed send ("remove +")
drop() {
  local before i; before=$(count 'remove +')
  kill "$2" 2>/dev/null; sleep 1
  for i in 1 2 3 4; do
    xadd "$1" "drop$i"; sleep 1
    [ "$(count 'remove +')" -gt "$before" ] && return 0
  done
  return 1
}

# --- preconditions ---
rc PING >/dev/null 2>&1 || { echo "Redis not reachable: run 'docker compose up -d redis' in notification-benchmark/"; exit 2; }
if curl -s -o /dev/null -m 1 "$BASE/actuator/health"; then
  echo "Something already listens on ${BASE##*:}. Stop it first:  lsof -nP -iTCP:8081 -sTCP:LISTEN  then  kill <PID>"
  exit 2
fi

is25 "$JAVA" || { echo "need Java 25, got: $("$JAVA" -version 2>&1 | head -1)  (set JAVA=/path/to/java25/bin/java)"; exit 2; }
echo "java: $JAVA"
echo "building jar..."
./gradlew -q :notification-imperative:bootJar || { echo "build failed"; exit 2; }
"$JAVA" -jar "$JAR" >"$LOG" 2>&1 &
APP_PID=$!
for i in $(seq 1 60); do curl -s -o /dev/null -m 1 "$BASE/actuator/health" && break; sleep 1; done
curl -s -o /dev/null -m 1 "$BASE/actuator/health" || { echo "app did not start, see log below"; tail -20 "$LOG"; exit 2; }
echo "app up (pid $APP_PID)"

S1=00000000-0000-0000-0000-0000000000d1   # C, R
S2=00000000-0000-0000-0000-0000000000d2   # Q
S3=00000000-0000-0000-0000-0000000000d3   # B
S4=00000000-0000-0000-0000-0000000000d4   # P, N
S5=00000000-0000-0000-0000-0000000000d5   # O, X
S6=00000000-0000-0000-0000-0000000000d6   # Z
S7=00000000-0000-0000-0000-0000000000d7   # S
for s in $S1 $S2 $S3 $S4 $S5 $S6 $S7; do rc DEL "$(key $s)" >/dev/null; done

# --- B: broadcast ---
echo "B  two subscribers, one event (step 2)"
sub $S3 "$TMP/b1"; B1=$PID
sub $S3 "$TMP/b2"; B2=$PID
sleep 3
xadd $S3 b1; sleep 1
check "B1 first curl"  "b1" "$(got "$TMP/b1")"
check "B2 second curl" "b1" "$(got "$TMP/b2")"
kill $B1 $B2 2>/dev/null

# --- C: close about 30s after the last subscriber is gone ---
echo "C  last subscriber leaves -> close about 30s later (waits up to 45s)"
sub $S1 "$TMP/c"; sleep 3
drop $S1 $PID || note "C0 app never logged a failed send after the curl died; timing from now"
if t=$(wait_for "time_wait for 30 seconds in store: $S1" 1 45); then
  if [ "$t" -ge 24 ] && [ "$t" -le 40 ]; then ok "C1 closed ${t}s after the dead curl was noticed (want ~30)"
  else bad "C1 closed ${t}s after the dead curl was noticed (want ~30)"; fi
else
  bad "C1 no 'time_wait ... $S1' log within 45s"
fi
if [ "$(count "StoreChannel for $S1 removed from Map")" -ge 1 ]; then ok "C2 channel removed from map"
else bad "C2 no 'StoreChannel for $S1 removed from Map' log"; fi

# --- R: a new subscriber after the close gets a fresh channel ---
echo "R  subscribe again after the close"
sub $S1 "$TMP/r"; R1=$PID; sleep 3
xadd $S1 r1; sleep 1
check "R1 new subscriber gets events" "r1" "$(got "$TMP/r")"
kill $R1 2>/dev/null

# --- Q: come back within 30s -> no close ---
echo "Q  leave, come back after ~5s, then wait 40s (no close expected)"
sub $S2 "$TMP/q1"; sleep 3
drop $S2 $PID || note "Q0 app never logged a failed send after the curl died"
sleep 5
sub $S2 "$TMP/q2"; Q2=$PID
sleep 40
if [ "$(count "time_wait for 30 seconds in store: $S2")" -eq 0 ]; then ok "Q1 channel stayed open"
else bad "Q1 channel closed although a subscriber came back"; fi
xadd $S2 q1; sleep 1
q=$(got "$TMP/q2")
case ",$q," in
  *,q1,*) ok "Q2 returning subscriber gets new events  (got: $q)";;
  *)      bad "Q2 returning subscriber did not get q1  (got: ${q:-<none>})";;
esac
[ "$q" = "q1" ] || note "Q3 returning subscriber also got events from before it subscribed (got: $q)"
kill $Q2 2>/dev/null

# --- P / N: replay from Last-Event-ID, and "from now" without it ---
echo "P  reconnect with Last-Event-ID = 3rd of 5 events (step 4)"
sub $S4 "$TMP/p0"; P0=$PID; sleep 3          # keeps the channel open
xid $S4 p1 >/dev/null; xid $S4 p2 >/dev/null; ID3=$(xid $S4 p3); xid $S4 p4 >/dev/null; xid $S4 p5 >/dev/null
sleep 1
check "P0 live subscriber got all five" "p1,p2,p3,p4,p5" "$(got "$TMP/p0")"
sub $S4 "$TMP/p1" "$ID3"; P1=$PID; sleep 1
check "P1 replay = only after the 3rd" "p4,p5" "$(got "$TMP/p1")"
echo "N  subscribe without Last-Event-ID (step 4)"
sub $S4 "$TMP/n"; N1=$PID; sleep 1
check "N1 nothing old" "" "$(got "$TMP/n")"
xid $S4 p6 >/dev/null; sleep 1
check "P2 replay, then live, no dup, in order" "p4,p5,p6" "$(got "$TMP/p1")"
check "N2 only events from now on" "p6" "$(got "$TMP/n")"
kill $P0 $P1 $N1 2>/dev/null

# --- O / X: RELOAD ---
echo "O  Last-Event-ID older than the channel start (step 4)"
OLD=$(xid $S5 o1); xid $S5 o2 >/dev/null     # written before any channel exists
sub $S5 "$TMP/o0"; O0=$PID; sleep 3          # channel starts here, at o2
sub $S5 "$TMP/o1" "$OLD"; O1=$PID; sleep 2
check "O1 one RELOAD_REQUIRED" "1" "$(reloads "$TMP/o1")"
if closed $O1; then ok "O2 connection closed after RELOAD"; else bad "O2 connection still open after RELOAD"; fi
check "O3 no normal events with the RELOAD" "" "$(grep '^data:' "$TMP/o1" 2>/dev/null | grep -v RELOAD_REQUIRED | sed -E 's/.*"eventId":"([^"]*)".*/\1/' | paste -sd, -)"
echo "X  malformed Last-Event-ID (step 4)"
sub $S5 "$TMP/x" "abc"; X1=$PID; sleep 2
check "X1 one RELOAD_REQUIRED (not 500)" "1" "$(reloads "$TMP/x")"
if closed $X1; then ok "X2 connection closed after RELOAD"; else bad "X2 connection still open after RELOAD"; fi
kill $O0 $O1 $X1 2>/dev/null

# --- Z: poison record ---
echo "Z  poison record (no type field) while a subscriber is connected (step 6)"
sub $S6 "$TMP/z1"; Z1=$PID; sleep 3
rc XADD "$(key $S6)" '*' eventId poison >/dev/null
if wait_for "StoreChannel for $S6 removed from Map" 1 5 >/dev/null; then ok "Z1 dead channel removed from map"
else bad "Z1 no 'StoreChannel for $S6 removed from Map' within 5s"; fi
sleep 1
if closed $Z1; then ok "Z2 subscriber's connection was completed (it can reconnect)"
else bad "Z2 subscriber still hanging on the dead channel"; fi
xadd $S6 z0                                   # a good record, so the newest one is not the poison
sub $S6 "$TMP/z3"; Z3=$PID; sleep 3
xadd $S6 z1; sleep 1
check "Z3 new subscriber gets a fresh, working channel" "z1" "$(got "$TMP/z3")"
kill $Z3 2>/dev/null
# known limit: if the poison record is the NEWEST one, a new channel cannot even start
rc XADD "$(key $S6)" '*' eventId poison2 >/dev/null
wait_for "StoreChannel for $S6 removed from Map" 2 5 >/dev/null
code=$(curl -s -o /dev/null -m 2 -w '%{http_code}' -X POST "$BASE/api/pos/$S6/notifications")
if [ "$code" = "500" ]; then note "Z4 poison record as newest -> subscribe returns 500 until a good record arrives (known limit, not fixed)"
else echo "  INFO Z4 poison record as newest -> subscribe answered '$code'"; fi

# --- S: shutdown (must be last: it stops the app) ---
echo "S  app shutdown with a subscriber connected (step 6)"
sub $S7 "$TMP/s1"; S1P=$PID; sleep 3
t0=$(date +%s)
kill -TERM "$APP_PID"
for i in $(seq 1 60); do kill -0 "$APP_PID" 2>/dev/null || break; sleep 1; done
t1=$(date +%s); took=$((t1 - t0))
if kill -0 "$APP_PID" 2>/dev/null; then bad "S1 app still running 60s after SIGTERM"; else ok "S1 app exited (${took}s)"; fi
APP_PID=""
if [ "$(count 'ExecutorService cleanly terminated')" -ge 1 ]; then ok "S2 executor terminated cleanly"
else bad "S2 no 'ExecutorService cleanly terminated' log"; fi
if [ "$(count "reading loop(broadcast) is ended: $S7")" -ge 1 ]; then ok "S3 reader loop for the store ended"
else bad "S3 no 'reading loop(broadcast) is ended: $S7' log"; fi
if closed $S1P; then ok "S4 subscriber's connection ended"; else bad "S4 subscriber still connected after shutdown"; fi
if [ "$took" -gt 5 ]; then
  note "S5 shutdown took ${took}s (>5s). Likely the web server's graceful shutdown waiting for open SSE requests before @PreDestroy runs"
fi

echo
echo "pass $pass / fail $fail / warn $warn"
[ "$fail" -eq 0 ]
