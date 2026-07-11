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
  140,
  '1.4.0',
  'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.4.0.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/releases/download/v1.4.0/rongrong-ledger-1.4.0.apk',
  'aa38ac03521596cb10f933a76857e58d68f8fdc72befc47d0f7a75ba3c4b649a',
  15384774,
  '1. 记账热力图支持近 3 月、近 6 月和全年切换，日期范围和笔数来自真实账本。
2. 绒绒日记更新完整首图、历史记录与编辑布局。
3. 清理未接入的微信登录、本地实名认证和未验收手机号入口，公开包只展示真实可用能力。
4. 产品官网新增 34 省级地区主题卡展，支持地区筛选、自动巡游、拖动切换和高清大图。
5. 管理后台连接恢复、月度备份、运维报告和下载线路检查进一步完善。',
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
  published_at = excluded.published_at,
  updated_at = excluded.updated_at;
