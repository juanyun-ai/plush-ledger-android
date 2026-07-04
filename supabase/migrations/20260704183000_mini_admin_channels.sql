alter table public.app_versions
  drop constraint if exists app_versions_platform_check;

alter table public.app_versions
  add constraint app_versions_platform_check
  check (platform in ('android', 'mini_program'));

alter table public.app_versions
  alter column apk_url drop not null,
  alter column sha256 drop not null,
  alter column file_size_bytes drop not null;

alter table public.app_versions
  drop constraint if exists app_versions_file_size_bytes_check,
  drop constraint if exists app_versions_android_download_required;

alter table public.app_versions
  add constraint app_versions_file_size_bytes_check
  check (file_size_bytes is null or file_size_bytes > 0),
  add constraint app_versions_android_download_required
  check (
    platform <> 'android'
    or (apk_url is not null and sha256 is not null and file_size_bytes is not null and file_size_bytes > 0)
  );

alter table public.app_versions
  drop constraint if exists app_versions_version_code_key;

create unique index if not exists app_versions_platform_version_code_idx
on public.app_versions(platform, version_code);

create or replace function public.publish_app_release_message()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.active then
    insert into public.official_messages (title, body, source_key, created_at, updated_at)
    values (
      case
        when new.platform = 'mini_program' then '绒绒记账小程序 v' || new.version_name || ' 更新'
        else '绒绒记账 v' || new.version_name || ' 更新'
      end,
      coalesce(nullif(new.release_notes, ''), '修复问题并改进使用体验。'),
      'release:' || new.platform || ':' || new.version_code::text,
      new.published_at,
      ((extract(epoch from clock_timestamp()) * 1000)::bigint)
    )
    on conflict (source_key) where source_key is not null do update set
      title = excluded.title,
      body = excluded.body,
      updated_at = excluded.updated_at;
  end if;
  return new;
end;
$$;

revoke all on function public.publish_app_release_message() from public, anon, authenticated;

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
  'mini_program',
  1508,
  '1.5.8',
  null,
  null,
  null,
  null,
  '优化日记分享图清晰度、分类管理、主题配色和账户图标展示。',
  false,
  true,
  ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  ((extract(epoch from clock_timestamp()) * 1000)::bigint)
)
on conflict (platform, version_code) do update set
  version_name = excluded.version_name,
  release_notes = excluded.release_notes,
  active = excluded.active,
  updated_at = excluded.updated_at;

insert into public.app_config (key, value, description, active, updated_at)
values
  (
    'mini_latest_version',
    '{"version":"1.5.8","versionCode":1508,"status":"uploaded"}'::jsonb,
    '小程序当前开发版本记录。',
    true,
    ((extract(epoch from clock_timestamp()) * 1000)::bigint)
  ),
  (
    'mini_home_notice',
    '{"enabled":false,"title":"","body":""}'::jsonb,
    '小程序首页轻量公告预留。',
    true,
    ((extract(epoch from clock_timestamp()) * 1000)::bigint)
  ),
  (
    'mini_share_cards',
    '{"enabled":true,"source":"province_cards"}'::jsonb,
    '小程序省份分享卡片开关。',
    true,
    ((extract(epoch from clock_timestamp()) * 1000)::bigint)
  )
on conflict (key) do update set
  value = excluded.value,
  description = excluded.description,
  active = excluded.active,
  updated_at = excluded.updated_at;
