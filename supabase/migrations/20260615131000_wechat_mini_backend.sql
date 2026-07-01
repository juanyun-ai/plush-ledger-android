create table if not exists public.mini_users (
  id uuid primary key default gen_random_uuid(),
  appid text not null,
  openid text not null,
  unionid text,
  nickname text,
  avatar_url text,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  unique (appid, openid)
);

create unique index if not exists mini_users_unionid_unique
on public.mini_users(unionid)
where unionid is not null;

create table if not exists public.mini_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.mini_users(id) on delete cascade,
  token_hash text not null unique,
  expires_at bigint not null,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  last_used_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

create table if not exists public.mini_ledger_snapshots (
  user_id uuid primary key references public.mini_users(id) on delete cascade,
  payload jsonb not null default '{}'::jsonb,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

create index if not exists mini_sessions_user_idx on public.mini_sessions(user_id);
create index if not exists mini_sessions_expiry_idx on public.mini_sessions(expires_at);
create index if not exists mini_ledger_snapshots_updated_idx on public.mini_ledger_snapshots(updated_at desc);

alter table public.mini_users enable row level security;
alter table public.mini_sessions enable row level security;
alter table public.mini_ledger_snapshots enable row level security;

revoke all on public.mini_users from anon, authenticated;
revoke all on public.mini_sessions from anon, authenticated;
revoke all on public.mini_ledger_snapshots from anon, authenticated;

grant select, insert, update, delete on public.mini_users to service_role;
grant select, insert, update, delete on public.mini_sessions to service_role;
grant select, insert, update, delete on public.mini_ledger_snapshots to service_role;
