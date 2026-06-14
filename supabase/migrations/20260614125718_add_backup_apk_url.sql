alter table public.app_versions
  add column if not exists backup_apk_url text;

comment on column public.app_versions.backup_apk_url is
  'Optional secondary HTTPS download URL used when the primary APK source fails.';
