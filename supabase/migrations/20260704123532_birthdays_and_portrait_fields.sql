alter table public.profiles
  add column if not exists province text,
  add column if not exists signature text;

alter table public.mini_users
  add column if not exists email text,
  add column if not exists email_verified_at bigint,
  add column if not exists province text,
  add column if not exists signature text,
  add column if not exists birthday_wechat_enabled boolean not null default false,
  add column if not exists birthday_email_enabled boolean not null default false,
  add column if not exists last_birthday_wechat_year integer,
  add column if not exists last_birthday_email_year integer;

create table if not exists public.mini_email_verifications (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.mini_users(id) on delete cascade,
  email text not null,
  code_hash text not null,
  expires_at bigint not null,
  consumed_at bigint,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

create index if not exists mini_email_verifications_user_idx
on public.mini_email_verifications(user_id, email, created_at desc);

create index if not exists mini_users_birthday_idx
on public.mini_users(birth_date)
where birth_date is not null;

comment on column public.profiles.province is 'Optional user-entered province. Do not infer or guess this value.';
comment on column public.profiles.signature is 'Optional user-entered profile signature.';
comment on column public.mini_users.email is 'Optional verified email for mini-program account features.';
comment on column public.mini_users.email_verified_at is 'Email verification time in epoch milliseconds.';
comment on column public.mini_users.province is 'Optional user-entered province from the mini-program ledger profile.';
comment on column public.mini_users.signature is 'Optional user-entered profile signature.';
comment on column public.mini_users.birthday_wechat_enabled is 'Whether the user opted in to birthday WeChat subscribe-message greetings.';
comment on column public.mini_users.birthday_email_enabled is 'Whether the user opted in to birthday email greetings.';

alter table public.mini_email_verifications enable row level security;
revoke all on public.mini_email_verifications from anon, authenticated;
grant select, insert, update, delete on public.mini_email_verifications to service_role;

update public.mini_users as user_row
set
  province = coalesce(nullif(left(btrim(snapshot.payload #>> '{profile,province}'), 80), ''), user_row.province),
  city = coalesce(nullif(left(btrim(snapshot.payload #>> '{profile,city}'), 80), ''), user_row.city),
  signature = coalesce(nullif(left(btrim(snapshot.payload #>> '{profile,signature}'), 120), ''), user_row.signature),
  email = coalesce(nullif(left(btrim(snapshot.payload #>> '{profile,email}'), 160), ''), user_row.email),
  birthday_wechat_enabled = coalesce(
    case
      when snapshot.payload #>> '{profile,birthdayWechatSubscribeEnabled}' in ('true', 'false')
        then (snapshot.payload #>> '{profile,birthdayWechatSubscribeEnabled}')::boolean
      else null
    end,
    user_row.birthday_wechat_enabled
  ),
  birthday_email_enabled = coalesce(
    case
      when snapshot.payload #>> '{profile,birthdayEmailEnabled}' in ('true', 'false')
        then (snapshot.payload #>> '{profile,birthdayEmailEnabled}')::boolean
      else null
    end,
    user_row.birthday_email_enabled
  ),
  updated_at = greatest(user_row.updated_at, snapshot.updated_at)
from public.mini_ledger_snapshots as snapshot
where snapshot.user_id = user_row.id;
