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
  130,
  '1.3.0',
  'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.3.0.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/raw/main/docs/downloads/rongrong-ledger-1.3.0.apk',
  '470a7f07c43a24aaee39d4df33b74022a489b22626fb0f61f4133b388733f9d9',
  34557236,
  '1. 安装新版后自动清理当前安装包和历史 APK 残留。
2. 启动时清理中断临时文件、过期分享图，并限制分享卡缓存上限。
3. 34 省分享卡改为按需下载缓存，安装包从约 97MB 降至约 33MB。
4. 补齐运维健康、Supabase 容量监控、月度备份和定期清理流程。
5. 合并界面适配、日记编辑、用户 ID、省市地区、记账热力图和色卡切换优化。',
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
