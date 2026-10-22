#!/bin/sh
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec "$ROOT_DIR/auth-api/mvnw" -f "$ROOT_DIR/pom.xml" "$@"