#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$ROOT_DIR/backups/supabase"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$BACKUP_DIR/$STAMP"
ARCHIVE="$BACKUP_DIR/plush-ledger-supabase-$STAMP.tar.gz"

mkdir -p "$WORK_DIR"
cd "$ROOT_DIR"

MODE="sql-dump"

cleanup_partial() {
  rm -rf "$WORK_DIR"
}

trap cleanup_partial ERR INT TERM

query_json() {
  local sql="$1"
  local output="$2"
  local temp="$output.tmp"
  local attempt

  for attempt in 1 2 3 4 5 6 7 8; do
    rm -f "$temp"
    if npx --offline supabase db query --linked "$sql" --output json > "$temp"; then
      mv "$temp" "$output"
      return 0
    fi
    # Supabase CLI may return a telemetry shutdown error after writing valid JSON.
    if [[ -s "$temp" ]] && jq -e '.rows | type == "array"' "$temp" >/dev/null 2>&1; then
      mv "$temp" "$output"
      return 0
    fi
    sleep $((attempt * 2))
  done

  rm -f "$temp"
  return 1
}

retry_command() {
  local attempt
  for attempt in 1 2 3 4 5 6 7 8; do
    if "$@"; then
      return 0
    fi
    sleep $((attempt * 2))
  done
  return 1
}

if docker info >/dev/null 2>&1; then
  echo "开始导出 Supabase schema..."
  retry_command npx --offline supabase db dump --linked --schema public,auth,storage --file "$WORK_DIR/schema.sql"

  echo "开始导出 Supabase data..."
  retry_command npx --offline supabase db dump --linked --data-only --use-copy --file "$WORK_DIR/data.sql"
else
  MODE="json-snapshot"
  echo "Docker Desktop 未运行，切换为 Supabase Management API JSON 快照。"
  query_json "select public.admin_ops_stats() as stats;" "$WORK_DIR/admin_ops_stats.json"

  FAILED_TABLES=()

  dump_table() {
    local schema="$1"
    local table="$2"
    local output="$WORK_DIR/${schema}_${table}.json"
    local sql="select coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) as rows from ${schema}.${table} t;"
    if query_json "$sql" "$output"; then
      echo "已导出 ${schema}.${table}"
    else
      FAILED_TABLES+=("${schema}.${table}")
    fi
  }

  for table in \
    profiles \
    transactions \
    categories \
    budgets \
    app_versions \
    app_config \
    feedback \
    official_messages \
    diary_entries \
    app_activity_events \
    mini_users \
    mini_sessions \
    mini_ledger_snapshots \
    mini_feedback \
    mini_profile_name_history
  do
    dump_table public "$table"
  done

  dump_table auth users
  dump_table auth identities
  dump_table storage buckets
  dump_table storage objects

  if (( ${#FAILED_TABLES[@]} > 0 )); then
    echo "备份失败，未导出：${FAILED_TABLES[*]}" >&2
    exit 1
  fi
fi

cat > "$WORK_DIR/MANIFEST.txt" <<EOF
Project: plush-ledger / tjcijqvweivqgqfpoehf
Created at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Mode: $MODE
Files:
- sql-dump mode: schema.sql + data.sql
- json-snapshot mode: admin_ops_stats.json + public_*.json + auth_*.json + storage_*.json

Notes:
- This backup does not include Supabase project secrets.
- Storage binary files should be backed up separately if they become product-critical.
- Supabase CLI sql-dump mode requires Docker Desktop. JSON snapshot mode is a readable safety copy, not a one-command restore artifact.
EOF

tar -czf "$ARCHIVE" -C "$BACKUP_DIR" "$STAMP"
tar -tzf "$ARCHIVE" >/dev/null
shasum -a 256 "$ARCHIVE" > "$ARCHIVE.sha256"
rm -rf "$WORK_DIR"
trap - ERR INT TERM

echo "备份完成：$ARCHIVE"
echo "完整性校验：$ARCHIVE.sha256"
