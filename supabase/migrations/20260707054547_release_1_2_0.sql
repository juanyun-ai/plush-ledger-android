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
  120,
  '1.2.0',
  'https://github.com/juanyun-ai/plush-ledger-android/releases/download/v1.2.0/rongrong-ledger-1.2.0.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/releases/download/v1.2.0/rongrong-ledger-1.2.0.apk',
  'b0b5b9c79cae3ddd5c084afeca54c63cfdd9961604590ec6492582698454fd4b',
  101698342,
  '1. 「我的」页改为真实记账足迹面板，菜单入口迁移到设置。
2. 个人信息增加性别标识、个性签名、生日和地区展示。
3. 修复绒绒日记同日多条与同步冲突问题，允许真实多条日记并去重旧重复内容。
4. 统计趋势柱支持按真实分类分层展示，点击可查看该周期构成。
5. 色卡恢复经典八色，主题列表改为更轻量的长条选择。',
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
