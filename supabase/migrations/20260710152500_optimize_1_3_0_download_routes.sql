update public.app_versions
set apk_url = 'https://tjcijqvweivqgqfpoehf.supabase.co/storage/v1/object/public/app-releases/rongrong-ledger-1.3.0.apk',
    backup_apk_url = 'https://github.com/juanyun-ai/plush-ledger-android/releases/download/v1.3.0/rongrong-ledger-1.3.0.apk',
    updated_at = (extract(epoch from now()) * 1000)::bigint
where platform = 'android'
  and version_code = 130;
