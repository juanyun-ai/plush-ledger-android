#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKUP_DIR="$ROOT_DIR/backups/supabase"
STAMP="$(date +%Y%m%d-%H%M%S)"
WORK_DIR="$BACKUP_DIR/$STAMP"
ARCHIVE="$BACKUP_DIR/plush-ledger-supabase-$STAMP.tar.gz"

mkdir -p "$WORK_DIR"
cd "$ROOT_DIR"

MODE="sql-dump"

if docker info >/dev/null 2>&1; then
  echo "开始导出 Supabase schema..."
  npx supabase db dump --linked --schema public,auth,storage --file "$WORK_DIR/schema.sql"

  echo "开始导出 Supabase data..."
  npx supabase db dump --linked --data-only --use-copy --file "$WORK_DIR/data.sql"
else
  MODE="json-snapshot"
  echo "Docker Desktop 未运行，切换为 Supabase Management API JSON 快照。"
  npx supabase db query --linked "select public.admin_ops_stats() as stats;" --output json > "$WORK_DIR/admin_ops_stats.json"

  dump_table() {
    local table="$1"
    local output="$WORK_DIR/public_${table}.json"
    local sql="select coalesce(jsonb_agg(to_jsonb(t)), '[]'::jsonb) as rows from public.${table} t;"
    if npx supabase db query --linked "$sql" --output json > "$output"; then
      echo "已导出 public.${table}"
    else
      echo "跳过 public.${table}: 导出失败或线上表不存在" > "$output"
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
    dump_table "$table"
  done
fi

cat > "$WORK_DIR/MANIFEST.txt" <<EOF
Project: plush-ledger / tjcijqvweivqgqfpoehf
Created at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
Mode: $MODE
Files:
- sql-dump mode: schema.sql + data.sql
- json-snapshot mode: admin_ops_stats.json + public_*.json

Notes:
- This backup does not include Supabase project secrets.
- Storage binary files should be backed up separately if they become product-critical.
- Supabase CLI sql-dump mode requires Docker Desktop. JSON snapshot mode is a readable safety copy, not a one-command restore artifact.
EOF

tar -czf "$ARCHIVE" -C "$BACKUP_DIR" "$STAMP"
rm -rf "$WORK_DIR"

echo "备份完成：$ARCHIVE"
