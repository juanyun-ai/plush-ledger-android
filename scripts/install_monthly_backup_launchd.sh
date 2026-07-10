#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PLIST_DIR="$HOME/Library/LaunchAgents"
PLIST_FILE="$PLIST_DIR/online.xiaoxing.rongrong.supabase-backup.plist"
LOG_DIR="$ROOT_DIR/backups/logs"

mkdir -p "$PLIST_DIR" "$LOG_DIR"

cat > "$PLIST_FILE" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>online.xiaoxing.rongrong.supabase-backup</string>
  <key>ProgramArguments</key>
  <array>
    <string>$ROOT_DIR/scripts/supabase_monthly_backup.sh</string>
  </array>
  <key>StartCalendarInterval</key>
  <dict>
    <key>Day</key>
    <integer>1</integer>
    <key>Hour</key>
    <integer>9</integer>
    <key>Minute</key>
    <integer>30</integer>
  </dict>
  <key>StandardOutPath</key>
  <string>$LOG_DIR/monthly-backup.out.log</string>
  <key>StandardErrorPath</key>
  <string>$LOG_DIR/monthly-backup.err.log</string>
  <key>RunAtLoad</key>
  <false/>
</dict>
</plist>
EOF

launchctl unload "$PLIST_FILE" >/dev/null 2>&1 || true
launchctl load "$PLIST_FILE"

echo "已安装每月 1 日 09:30 自动备份：$PLIST_FILE"
