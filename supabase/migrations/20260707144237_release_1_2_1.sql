update public.app_versions
set
  apk_url = 'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.2.0.apk',
  backup_apk_url = 'https://github.com/juanyun-ai/plush-ledger-android/raw/main/docs/downloads/rongrong-ledger-1.2.0.apk',
  updated_at = ((extract(epoch from clock_timestamp()) * 1000)::bigint)
where platform = 'android'
  and version_code = 120;

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
  121,
  '1.2.1',
  'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.2.1.apk',
  'https://github.com/juanyun-ai/plush-ledger-android/raw/main/docs/downloads/rongrong-ledger-1.2.1.apk',
  '57fc62fd208651f7291380a336aad0f4a788430936ac3e844a24cb6976281d97',
  101911390,
  '1. 主题色卡恢复为经典、国内、国外三组：经典保留原八种色系，国内恢复 34 个省级行政区，国外恢复国家/地区色卡。
2. 我的页增加窄屏保护和界面字号设置，足迹金额改为短金额格式，减少部分机型数值省略号。
3. 更新下载弹窗新增后台下载，慢网下可退出弹窗继续下载；主下载线路切回 GitHub raw。
4. 账单智能导入新增绒绒备份来源，支持小程序和 App 导出的 CSV/JSON 备份。
5. 记账足迹改为两行三列，热力图按周对齐并显示连续月份标签。',
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
