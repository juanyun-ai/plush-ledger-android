create unique index if not exists app_versions_platform_version_code_idx
on public.app_versions(platform, version_code);

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
  115,
  '1.1.5',
  'https://privacy.xiaoxing.online/downloads/rongrong-ledger-1.1.5.apk',
  null,
  '2a66d82ac12e001cf127ba93c588c929c59dda529668c9a3d01c2f2221b7b378',
  101698342,
  '1. 34 省级分享卡片重新同步为最新原图素材，并补齐重庆限定卡片。
2. 本地模式补充切换到邮箱登录前需自行 CSV 导出数据的提醒。
3. 管理后台连接失败提示更清晰，并重新部署 admin-console 后端函数。
4. 官网下载地址和应用内版本推送同步到 Android 1.1.5。',
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
