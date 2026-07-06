alter table public.mini_users
  add column if not exists account_no text,
  add column if not exists account_no_changed_year text,
  add column if not exists account_no_changed_count integer not null default 0,
  add column if not exists district text;

create unique index if not exists mini_users_account_no_unique_idx
on public.mini_users (lower(account_no))
where account_no is not null and btrim(account_no) <> '';

create table if not exists public.mini_profile_name_history (
  id text primary key,
  user_id uuid not null references public.mini_users(id) on delete cascade,
  old_display_name text not null default '',
  new_display_name text not null default '',
  changed_at bigint not null,
  source text not null default 'mini',
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

alter table public.mini_profile_name_history enable row level security;

revoke all on public.mini_profile_name_history from anon, authenticated;
grant select, insert, update, delete on public.mini_profile_name_history to service_role;

create index if not exists mini_profile_name_history_user_changed_idx
on public.mini_profile_name_history(user_id, changed_at desc);

update public.mini_users as user_row
set district = coalesce(
  nullif(left(btrim(snapshot.payload #>> '{profile,district}'), 80), ''),
  user_row.district
)
from public.mini_ledger_snapshots as snapshot
where snapshot.user_id = user_row.id;

comment on column public.mini_users.account_no is 'User-facing mini-program ID, 4-12 chars, generated on first cloud login and user-editable at most twice per year.';
comment on column public.mini_users.account_no_changed_year is 'Calendar year for account_no_changed_count.';
comment on column public.mini_users.account_no_changed_count is 'User-facing ID changes used in account_no_changed_year.';
comment on column public.mini_users.district is 'Optional user-selected district/county from the WeChat mini-program region picker.';
