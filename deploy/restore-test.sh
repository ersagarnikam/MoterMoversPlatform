#!/bin/sh
# Proves a backup can be restored: loads it into a throwaway PostgreSQL container, checks the schema and a few
# row counts, then removes the container. Nothing touches the live database.
#
#   deploy/restore-test.sh                              # newest file in deploy/backups
#   deploy/restore-test.sh path/to/motormovers-YYYYMMDD.sql.gz
#
# Run it after the first night in production, then at least once a term (see RUNBOOK.md, Backups).
set -eu

dir=$(cd "$(dirname "$0")" && pwd)
backup=${1:-$(ls -1t "$dir"/backups/motormovers-*.sql.gz 2>/dev/null | head -n 1)}
if [ -z "$backup" ] || [ ! -f "$backup" ]; then
  echo "No backup found. Pass a file, or check deploy/backups." >&2
  exit 1
fi

name=mm-restore-test-$$
cleanup() { docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "Restoring $(basename "$backup") into a throwaway database..."
docker run -d --name "$name" -e POSTGRES_PASSWORD=restore-test -e POSTGRES_DB=restore postgres:17-alpine >/dev/null
tries=0
until docker exec "$name" pg_isready -U postgres -d restore >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [ "$tries" -gt 60 ]; then
    echo "The throwaway database did not start." >&2
    exit 1
  fi
  sleep 1
done
# The init scripts restart the server once; wait for that too.
sleep 3
until docker exec "$name" pg_isready -U postgres -d restore >/dev/null 2>&1; do sleep 1; done

gunzip -c "$backup" | docker exec -i "$name" psql -q -U postgres -d restore -v ON_ERROR_STOP=1 >/dev/null

query() { docker exec "$name" psql -U postgres -d restore -tAc "$1"; }
version=$(query "select max(version::int) from flyway_schema_history where success")
echo "Schema version: V$version"
for table in app_user school school_class child licence payment programme_session; do
  echo "  $table: $(query "select count(*) from $table") rows"
done
if [ "$(query "select count(*) from app_user")" -eq 0 ]; then
  echo "The restored database has no accounts. Check the backup." >&2
  exit 1
fi
echo "Restore test passed."
