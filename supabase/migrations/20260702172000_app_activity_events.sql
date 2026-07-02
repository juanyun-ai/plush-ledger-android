create table if not exists public.app_activity_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  event_type text not null default 'app_open',
  device_brand text,
  device_model text,
  device_platform text,
  app_version text,
  occurred_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

alter table public.app_activity_events enable row level security;

drop policy if exists app_activity_events_own_insert on public.app_activity_events;
create policy app_activity_events_own_insert
on public.app_activity_events for insert to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists app_activity_events_own_read on public.app_activity_events;
create policy app_activity_events_own_read
on public.app_activity_events for select to authenticated
using ((select auth.uid()) = user_id);

grant select, insert on public.app_activity_events to authenticated;
grant select, insert, update, delete on public.app_activity_events to service_role;

create index if not exists app_activity_events_user_time_idx
  on public.app_activity_events (user_id, occurred_at desc);

create index if not exists app_activity_events_time_idx
  on public.app_activity_events (occurred_at desc);

comment on table public.app_activity_events is 'Lightweight Android app activity events for real admin analytics. No ledger content is stored here.';
comment on column public.app_activity_events.event_type is 'Client event type, for example app_open or sign_in.';
