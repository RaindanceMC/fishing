#!/usr/bin/env bash
# Boots a real Paper server with RainsCore and the freshly built RainsFishing, twice (the second boot reads what the
# first wrote), and checks that fishing actually comes up: the module's "Fishing is up" line with every fish, dish
# and recipe, its resource pack written out for Core, no exception anywhere in the log, every YAML a plugin wrote
# still parsing, and a clean stop over RCON.
#
# Usage: scripts/live-server-test.sh [--keep]     (run `mvn install` first)

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
PAPER_VERSION="26.3"
PAPER_BUILD="169"
CACHE="${RAINS_E2E_CACHE:-$HOME/.cache/rains-e2e}"
CORE_VERSION="$(sed -n 's|.*<rainscore.version>\(.*\)</rainscore.version>.*|\1|p' "$ROOT/pom.xml")"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-180}"
PORT="${PORT:-25591}"
RCON_PORT="${RCON_PORT:-25592}"

KEEP=0
[ "${1:-}" = "--keep" ] && KEEP=1
WORKDIR="$(mktemp -d /tmp/rains-fishing-live.XXXXXX)"
PID=""
cleanup() {
  if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
    kill "$PID" 2>/dev/null || true
    for _ in $(seq 1 15); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
    kill -9 "$PID" 2>/dev/null || true
  fi
  if [ "$KEEP" = "1" ]; then echo "Kept: $WORKDIR"; else rm -rf "$WORKDIR"; fi
}
trap cleanup EXIT
log()  { echo "[fishing-live] $*"; }
fail() { echo "[fishing-live] FAIL: $*" >&2; exit 1; }

mkdir -p "$CACHE"
PAPER="$CACHE/paper-$PAPER_VERSION-$PAPER_BUILD.jar"
if [ ! -s "$PAPER" ]; then
  meta="$(curl -sf "https://fill.papermc.io/v3/projects/paper/versions/$PAPER_VERSION/builds/$PAPER_BUILD")"
  url="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["downloads"]["server:default"]["url"])' <<<"$meta")"
  curl -sf -o "$PAPER" "$url" || fail "could not download Paper"
fi
CORE="$HOME/.m2/repository/de/raindancer/RainsCore/$CORE_VERSION/RainsCore-$CORE_VERSION.jar"
[ -s "$CORE" ] || fail "RainsCore $CORE_VERSION is not in ~/.m2 — run mvn install first"
CATALOG="$ROOT/fishing-module/src/main/resources/de/raindancer/modules/fishing/catalog.json"
FISH="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["fish"]))' "$CATALOG")"
DISHES="$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["dishes"]))' "$CATALOG")"
PLUGIN="$(find "$ROOT/fishing-standalone/target" -maxdepth 1 -name 'RainsFishing-*.jar' ! -name 'original-*' | head -1)"
[ -n "$PLUGIN" ] || fail "no RainsFishing jar — run mvn install first"

SERVER="$WORKDIR/server"
mkdir -p "$SERVER/plugins"
cp "$PAPER" "$SERVER/paper.jar"
cp "$CORE" "$PLUGIN" "$SERVER/plugins/"
echo "eula=true" > "$SERVER/eula.txt"
cat > "$SERVER/server.properties" <<PROPS
online-mode=false
enable-rcon=true
rcon.port=$RCON_PORT
rcon.password=fishing-live
server-port=$PORT
level-seed=1
view-distance=4
simulation-distance=4
white-list=false
PROPS

boot() {
  local round="$1"
  local out="$WORKDIR/boot-$round.log"
  ( cd "$SERVER" && exec java -Xmx2G -jar paper.jar --nogui >"$out" 2>&1 ) &
  PID=$!
  local waited=0
  until grep -q 'Done (' "$out" 2>/dev/null; do
    kill -0 "$PID" 2>/dev/null || { tail -40 "$out"; fail "boot $round: the server died"; }
    sleep 2; waited=$((waited + 2))
    [ "$waited" -lt "$STARTUP_TIMEOUT" ] || { tail -40 "$out"; fail "boot $round: no Done in ${STARTUP_TIMEOUT}s"; }
  done
  log "boot $round: up after ${waited}s"
  grep -q "Fishing is up: $FISH fish and $DISHES dishes ($((DISHES * 4)) recipes), 6 rods" "$out" \
    || { grep -i fish "$out" | tail -20; fail "boot $round: fishing did not come up with every fish, dish and recipe"; }
  python3 "$SCRIPT_DIR/rcon.py" 127.0.0.1 "$RCON_PORT" fishing-live "fishing" >/dev/null || true
  python3 "$SCRIPT_DIR/rcon.py" 127.0.0.1 "$RCON_PORT" fishing-live "fishing give nobody fish:pebble_trout" >/dev/null || true
  sleep 2
  python3 "$SCRIPT_DIR/rcon.py" 127.0.0.1 "$RCON_PORT" fishing-live "stop" >/dev/null || true
  for _ in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done
  kill -0 "$PID" 2>/dev/null && fail "boot $round: did not stop"
  PID=""
  if grep -nE 'Exception|Caused by|\[ERROR\]|SEVERE' "$out" | grep -viE 'rcon|Unable to load the native' ; then
    fail "boot $round: something threw — see above"
  fi
  local files
  files="$(find "$SERVER/plugins/RainsFishing/pack/looks" -type f 2>/dev/null | wc -l)"
  [ "$files" -ge 200 ] || fail "boot $round: the resource pack was not written out ($files files)"
  log "boot $round: clean, $files pack files out"
}

boot 1
boot 2
python3 - "$SERVER/plugins" <<'PY' || fail "a plugin wrote YAML that no longer parses"
import os, sys
try:
    import yaml
except ImportError:
    sys.exit(0)
for root, _, names in os.walk(sys.argv[1]):
    for name in names:
        if name.endswith(".yml"):
            with open(os.path.join(root, name)) as handle:
                yaml.safe_load(handle)
PY
log "PASSED: RainsFishing boots twice, cleanly"
