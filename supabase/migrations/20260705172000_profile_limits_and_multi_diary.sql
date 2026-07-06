drop index if exists public.diary_entries_user_date_idx;

create index if not exists diary_entries_user_date_idx
  on public.diary_entries(user_id, date);

create table if not exists public.profile_name_history (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  old_display_name text not null default '',
  new_display_name text not null default '',
  changed_at bigint not null,
  source text not null default 'android',
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

alter table public.profile_name_history enable row level security;

drop policy if exists profile_name_history_own_select on public.profile_name_history;
create policy profile_name_history_own_select
on public.profile_name_history for select to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists profile_name_history_own_insert on public.profile_name_history;
create policy profile_name_history_own_insert
on public.profile_name_history for insert to authenticated
with check ((select auth.uid()) = user_id);

grant select, insert on public.profile_name_history to authenticated;
grant select, insert, update, delete on public.profile_name_history to service_role;

create index if not exists profile_name_history_user_changed_idx
  on public.profile_name_history(user_id, changed_at desc);
