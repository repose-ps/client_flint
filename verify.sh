#!/bin/sh
set -eu

if [ "$#" -gt 1 ]; then
    echo "Usage: ./verify.sh [revision-377-rscache-directory]" >&2
    exit 2
fi

CACHE=${1:-${RSCACHE_DIR:-}}
if [ -z "$CACHE" ]; then
    echo "Usage: ./verify.sh <revision-377-rscache-directory>" >&2
    echo "   or: RSCACHE_DIR=/path/to/rscache ./verify.sh" >&2
    exit 2
fi

exec "$(dirname "$0")/mvnw" clean verify -Drscache="$CACHE"
