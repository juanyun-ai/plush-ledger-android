create table if not exists public.app_config (
  key text primary key,
  value jsonb not null default '{}'::jsonb,
  description text not null default '',
  active boolean not null default true,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

alter table public.app_config enable row level security;

drop policy if exists app_config_public_read on public.app_config;
create policy app_config_public_read
on public.app_config for select to anon, authenticated
using (active = true);

grant select on public.app_config to anon, authenticated;

insert into public.app_config (key, value, description)
values
  (
    'home_notice',
    '{"enabled": false, "title": "", "body": ""}'::jsonb,
    '预留：首页或启动后的轻量公告。当前 App 版本尚未读取。'
  ),
  (
    'cloud_ai_enabled',
    '{"enabled": true}'::jsonb,
    '预留：远程控制云端 AI 增强入口。当前 App 版本尚未读取。'
  ),
  (
    'support_links',
    '{"email": "support@xiaoxing.online", "website": "https://privacy.xiaoxing.online/"}'::jsonb,
    '预留：支持邮箱、官网和帮助入口。当前 App 版本尚未读取。'
  )
on conflict (key) do nothing;
