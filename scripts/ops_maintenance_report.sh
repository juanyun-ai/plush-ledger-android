#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_DIR="$ROOT_DIR/reports/ops"
STAMP="$(date +%Y-%m-%d)"
REPORT_FILE="$REPORT_DIR/ops-maintenance-$STAMP.md"
TMP_STATS="$(mktemp)"
TMP_VERSIONS="$(mktemp)"
trap 'rm -f "$TMP_STATS" "$TMP_VERSIONS"' EXIT

mkdir -p "$REPORT_DIR"
cd "$ROOT_DIR"

npx supabase db query --linked "select public.admin_ops_stats() as stats;" --output json > "$TMP_STATS"
npx supabase db query --linked "select platform, version_code, version_name, active, apk_url, backup_apk_url, file_size_bytes, updated_at from public.app_versions order by version_code desc limit 20;" --output json > "$TMP_VERSIONS"

DOCS_DOWNLOAD_COUNT="$(find docs/downloads -maxdepth 1 -type f -name '*.apk' 2>/dev/null | wc -l | tr -d ' ')"
DOCS_DOWNLOAD_SIZE="$(du -sh docs/downloads 2>/dev/null | awk '{print $1}')"
SHARE_CARD_COUNT="$(find docs/share-cards -maxdepth 1 -type f 2>/dev/null | wc -l | tr -d ' ')"
SHARE_CARD_SIZE="$(du -sh docs/share-cards 2>/dev/null | awk '{print $1}')"
BUILD_SIZE="$(du -sh build app/build 2>/dev/null | sed 's/[[:space:]]\\+/ /g')"

node - "$TMP_STATS" "$TMP_VERSIONS" "$REPORT_FILE" "$DOCS_DOWNLOAD_COUNT" "$DOCS_DOWNLOAD_SIZE" "$SHARE_CARD_COUNT" "$SHARE_CARD_SIZE" "$BUILD_SIZE" <<'NODE'
const fs = require("fs");
const [statsPath, versionsPath, outPath, docsCount, docsSize, shareCount, shareSize, buildSize] = process.argv.slice(2);
const parseRows = (file) => {
  const raw = fs.readFileSync(file, "utf8");
  const jsonStart = raw.indexOf("{");
  const parsed = JSON.parse(raw.slice(jsonStart));
  return parsed.rows || [];
};
const stats = parseRows(statsPath)[0]?.stats || parseRows(statsPath)[0]?.admin_ops_stats || {};
const versions = parseRows(versionsPath);
const bytes = (value) => {
  const n = Number(value || 0);
  if (!n) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  let size = n;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size >= 10 || index === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[index]}`;
};
const pct = (value, limit) => limit ? `${(Number(value || 0) / Number(limit) * 100).toFixed(1)}%` : "-";
const db = stats.database || {};
const storage = Array.isArray(stats.storage) ? stats.storage : [];
const storageTotal = storage.reduce((sum, item) => sum + Number(item.bytes || 0), 0);
const latestAndroid = versions.find((row) => row.platform === "android" && row.active !== false) || versions.find((row) => row.platform === "android") || {};
const usesTos = [latestAndroid.apk_url, latestAndroid.backup_apk_url].some((url) => /volc|tos|ivolces|byteimg|volces/i.test(String(url || "")));
const warnings = [];
if (Number(db.bytes || 0) > 350 * 1024 * 1024) warnings.push("数据库已接近免费额度预警线。");
if (storageTotal > 800 * 1024 * 1024) warnings.push("Storage 已接近免费额度预警线。");
if (!usesTos) warnings.push("当前最新 APK 下载链路未发现火山 TOS。");
if (Number(docsCount) > 6) warnings.push("docs/downloads APK 数量偏多，建议只保留当前版、上一版和必要回滚包。");
const md = `# 绒绒记账月度运维巡检

生成时间：${new Date().toLocaleString("zh-CN", { hour12: false })}

## 核心容量

| 项目 | 当前 | 免费额度 | 使用率 |
| --- | ---: | ---: | ---: |
| Supabase 数据库 | ${bytes(db.bytes)} | 500 MB | ${pct(db.bytes, db.limit_bytes || 524288000)} |
| Supabase Storage | ${bytes(storageTotal)} | 1 GB | ${pct(storageTotal, 1073741824)} |

## Storage 分桶

| 桶 | 对象数 | 占用 |
| --- | ---: | ---: |
${storage.map((item) => `| ${item.bucket_id} | ${item.object_count || 0} | ${bytes(item.bytes)} |`).join("\n") || "| - | 0 | 0 B |"}

## 发布资产

- 最新 Android 版本：${latestAndroid.version_name ? `v${latestAndroid.version_name} (${latestAndroid.version_code})` : "未找到"}
- 最新 APK 体积：${bytes(latestAndroid.file_size_bytes)}
- 火山 TOS 承载：${usesTos ? "已在下载链路中出现" : "当前未发现"}
- docs/downloads：${docsCount} 个 APK，${docsSize}
- docs/share-cards：${shareCount} 个素材，${shareSize}

## 本地构建占用

\`\`\`text
${buildSize.trim() || "未发现 build 目录"}
\`\`\`

## 本月动作

- [ ] 生成 Supabase 逻辑备份并保存到本地私有目录。
- [ ] 检查 GitHub Releases 和 docs/downloads 是否堆积旧 APK。
- [ ] 抽查官网、管理后台、APK 主备下载链接。
- [ ] 检查未处理反馈和最新版本推送状态。

## 预警

${warnings.length ? warnings.map((item) => `- ${item}`).join("\n") : "- 暂无容量预警。"}
`;
fs.writeFileSync(outPath, md);
console.log(outPath);
NODE
