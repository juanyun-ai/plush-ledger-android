update public.app_versions
set apk_url = 'https://raw.githubusercontent.com/juanyun-ai/plush-ledger-android/main/docs/downloads/rongrong-ledger-1.1.5.apk',
    backup_apk_url = 'https://github.com/juanyun-ai/plush-ledger-android/raw/main/docs/downloads/rongrong-ledger-1.1.5.apk',
    updated_at = ((extract(epoch from clock_timestamp()) * 1000)::bigint)
where platform = 'android'
  and version_code = 115;
