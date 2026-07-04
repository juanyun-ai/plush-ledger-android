create table if not exists public.diary_entries (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  date text not null,
  text text not null default '',
  mood text not null default '开心',
  status text not null default '',
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  deleted_at bigint
);

alter table public.diary_entries enable row level security;

drop policy if exists diary_entries_own_select on public.diary_entries;
create policy diary_entries_own_select
on public.diary_entries for select to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists diary_entries_own_insert on public.diary_entries;
create policy diary_entries_own_insert
on public.diary_entries for insert to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists diary_entries_own_update on public.diary_entries;
create policy diary_entries_own_update
on public.diary_entries for update to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists diary_entries_own_delete on public.diary_entries;
create policy diary_entries_own_delete
on public.diary_entries for delete to authenticated
using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.diary_entries to authenticated;
grant select, insert, update, delete on public.diary_entries to service_role;

create unique index if not exists diary_entries_user_date_idx
  on public.diary_entries(user_id, date);

create index if not exists diary_entries_user_updated_idx
  on public.diary_entries(user_id, updated_at desc);

comment on table public.diary_entries is 'Android app diary entries synced across devices. Deleted rows are retained as tombstones via deleted_at.';
