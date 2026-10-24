#!/usr/bin/env bash
# MVC (notification-mvc-vt) behavior check — step 3 cases.
#   A: no header  -> "from now": old events are NOT sent, an event added after the first empty BLOCK IS sent
#   B: Last-Event-ID -> only events after that id
#   C: empty stream -> connection stays open (no 500), first event arrives
#
# Before running (from notification-benchmark/):
#   docker compose up -d redis
#   ./gradlew :notification-mvc-vt:bootRun        # in another terminal
# Run:
#   ./load/mvc-check.sh
#
# Uses its own store id, so it does not touch the stream you test by hand.
set -u
cd "$(dirname "$0")/.."

BASE=${BASE_URL:-http://localhost:8081}
STORE=${STORE_ID:-00000000-0000-0000-0000-0000000000c1}
K="notification:store:$STORE:pos"
U="$BASE/api/pos/$STORE/notifications"
TMP=$(mktemp -d)
PIDS=()
pass=0; fail=0

cleanup() { for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null; done; rm -rf "$TMP"; }
trap cleanup EXIT

rc()   { docker compose exec -T redis redis-cli "$@"; }
# same fields as the load generator, so a strict MapRecord -> SseEvent mapping (type, timestamp required) accepts them
xadd() { rc XADD "$K" '*' eventId "$1" type TEST payload '{}' timestamp "$(date +%s)000" | tr -d '"\r'; }

# sub FILE [LAST_EVENT_ID] -> starts curl in background, sets PID
sub() {
  if [ -n "${2:-}" ]; then
    curl -sN -X POST -H "Last-Event-ID: $2" "$U" >"$1" 2>&1 &
  else
    curl -sN -X POST "$U" >"$1" 2>&1 &
  fi
  PID=$!; PIDS+=("$PID")
}
# eventIds received so far, comma-separated, in arrival order
got() { grep '^data:' "$1" 2>/dev/null | sed -E 's/.*"eventId":"([^"]*)".*/\1/' | paste -sd, -; }
check() { # NAME WANT GOT
  if [ "$2" = "$3" ]; then echo "  PASS $1  (got: ${3:-<none>})"; pass=$((pass+1))
  else echo "  FAIL $1  want: ${2:-<none>}  got: ${3:-<none>}"; fail=$((fail+1)); fi
}
alive() { # NAME PID FILE
  if kill -0 "$2" 2>/dev/null; then echo "  PASS $1  (connection open)"; pass=$((pass+1))
  else echo "  FAIL $1  connection closed. server said:"; sed 's/^/        /' "$3" | head -5; fail=$((fail+1)); fi
}

# --- preconditions ---
rc PING >/dev/null 2>&1 || { echo "Redis not reachable: run 'docker compose up -d redis' in notification-benchmark/"; exit 2; }
curl -s -o /dev/null -m 2 "$BASE/actuator/health" || { echo "App not reachable at $BASE: run './gradlew :notification-mvc-vt:bootRun'"; exit 2; }
echo "store: $STORE"

# --- C: empty stream ---
echo "C  empty stream"
rc DEL "$K" >/dev/null
sub "$TMP/c"; sleep 1
alive "C1 subscribe to empty stream" "$PID" "$TMP/c"
xadd c1 >/dev/null; sleep 1
check "C2 first event arrives" "c1" "$(got "$TMP/c")"
kill "$PID" 2>/dev/null

# --- A: no header = from now ---
echo "A  no header (from now)"
OLD1=$(xadd old1); xadd old2 >/dev/null
sub "$TMP/a"; sleep 3            # > BLOCK 2s, so the first poll ends empty
check "A1 old events not sent" "" "$(got "$TMP/a")"
xadd new1 >/dev/null; sleep 1
check "A2 event after first empty poll arrives" "new1" "$(got "$TMP/a")"
kill "$PID" 2>/dev/null

# --- B: Last-Event-ID ---
echo "B  Last-Event-ID = old1 ($OLD1)"
sub "$TMP/b" "$OLD1"; sleep 1
check "B1 only events after old1" "old2,new1" "$(got "$TMP/b")"
kill "$PID" 2>/dev/null

echo
echo "pass $pass / fail $fail"
[ "$fail" -eq 0 ]
