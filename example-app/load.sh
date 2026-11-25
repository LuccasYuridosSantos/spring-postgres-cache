#!/usr/bin/env bash
# Generates traffic so the Grafana dashboard has something to show.
# Picks product ids from a small pool, so most requests become cache hits
# and misses come back every time an entry expires (default-ttl 30s).
# Usage: bash example-app/load.sh [duration-seconds] [parallel-workers]
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${1:-120}"
WORKERS="${2:-4}"
END=$(( $(date +%s) + DURATION ))

worker() {
  while [ "$(date +%s)" -lt "$END" ]; do
    curl -s -o /dev/null "$BASE_URL/products/$(( RANDOM % 20 ))"
    sleep 0.1
  done
}

echo "Sending traffic to $BASE_URL for ${DURATION}s with $WORKERS workers..."
for _ in $(seq 1 "$WORKERS"); do worker & done
wait
echo "Done."
