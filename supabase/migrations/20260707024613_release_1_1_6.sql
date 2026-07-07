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
  116,
  '1.1.6',
  'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.1.6.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/raw/main/docs/downloads/rongrong-ledger-1.1.6.apk',
  'b390eff00b6d4dd6c1ff2034e17fa7e42f301d826ae4144cb437c273870bb06f',
  101698342,
  '1. 修复更新包下载地址返回旧页面导致完整性校验失败的问题。
2. 浏览器下载会打开当前版本真实 APK 地址。
3. 增加视觉字号保护，减少系统字体放大机型的页面挤压和换行。',
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
