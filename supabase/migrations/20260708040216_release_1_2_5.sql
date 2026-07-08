insert into public.app_versions (
  platform,
  version_code,
  version_name,
  apk_url,
  backup_apk_url,
  sha256,
  file_size_bytes,
  release_notes,
  is_mandatory,
  active,
  published_at,
  updated_at
) values (
  'android',
  125,
  '1.2.5',
  'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.2.5.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/raw/main/docs/downloads/rongrong-ledger-1.2.5.apk',
  'f778234051d22c3c25dbefda1428732e8bd95677879cc06de6db54a8f698d83b',
  101911390,
  '1. 界面字号移除放大选项，新增更紧凑模式，并限制系统字号继续放大界面，改善 vivo 等显示偏大机型的排版。
2. 记账足迹改为自适应卡片，数值不再被截断；热力图保留为默认折叠的“记账活跃度”。
3. 绒绒日记历史编辑暂存按单篇日记保存，勾选合并入口移动到近期日记标题右侧。
4. 用户ID限制为 6-8 位英文字母并云端查重，地区选择补齐省市列表。
5. 主题色卡改为先预览再确认切换，经典色恢复原八种纯色。',
  false,
  true,
  ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  ((extract(epoch from clock_timestamp()) * 1000)::bigint)
)
on conflict (platform, version_code) do update set
  version_name = excluded.version_name,
  apk_url = excluded.apk_url,
  backup_apk_url = excluded.backup_apk_url,
  sha256 = excluded.sha256,
  file_size_bytes = excluded.file_size_bytes,
  release_notes = excluded.release_notes,
  is_mandatory = excluded.is_mandatory,
  active = excluded.active,
  updated_at = excluded.updated_at;
