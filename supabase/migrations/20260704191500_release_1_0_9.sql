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
  110,
  '1.0.9',
  'https://privacy.xiaoxing.online/downloads/rongrong-ledger-1.0.9.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/releases/download/v1.0.9/rongrong-ledger-1.0.9.apk',
  'b06c614a4b6eea7427a6129057f0742bbf2cbb7bfd94e403f0c827297fffdfad',
  11633365,
  '1. 管理后台新增 App / 小程序来源筛选，反馈、官方消息、版本更新和远程配置都能分开查看。
2. App 多个页面换上不同动作的绒绒形象，欢迎、记账、统计、日记和设置页更有区分度。
3. 绒绒日记主视觉换成小程序同款风格，并使用透明动作图避免白底和边框。
4. 后台布局优化，官方消息和版本更新的左侧表单不再被右侧列表撑得过长。',
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
