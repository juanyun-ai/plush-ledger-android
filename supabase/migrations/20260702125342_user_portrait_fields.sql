alter table public.profiles
  add column if not exists city text,
  add column if not exists device_brand text,
  add column if not exists device_model text,
  add column if not exists device_platform text,
  add column if not exists device_last_seen_at bigint,
  add column if not exists app_version text;

alter table public.mini_users
  add column if not exists gender text,
  add column if not exists birth_date date,
  add column if not exists city text,
  add column if not exists device_brand text,
  add column if not exists device_model text,
  add column if not exists device_platform text,
  add column if not exists app_version text,
  add column if not exists last_seen_at bigint;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'mini_users_gender_valid'
      and conrelid = 'public.mini_users'::regclass
  ) then
    alter table public.mini_users
      add constraint mini_users_gender_valid
      check (gender is null or gender in ('female', 'male', 'other', 'prefer_not'));
  end if;
end $$;

comment on column public.profiles.city is 'Optional user-entered city. Do not infer or guess this value.';
comment on column public.profiles.device_brand is 'Last reported Android device manufacturer.';
comment on column public.profiles.device_model is 'Last reported Android device model.';
comment on column public.profiles.device_platform is 'Last reported client platform.';
comment on column public.profiles.device_last_seen_at is 'Last profile telemetry update time in epoch milliseconds.';
comment on column public.profiles.app_version is 'Last reported Android app version.';
comment on column public.mini_users.city is 'Optional user-entered city from the mini-program ledger profile.';
comment on column public.mini_users.device_brand is 'Last reported WeChat mini-program device brand.';
comment on column public.mini_users.device_model is 'Last reported WeChat mini-program device model.';
comment on column public.mini_users.device_platform is 'Last reported WeChat mini-program platform.';
comment on column public.mini_users.last_seen_at is 'Last mini-program login or sync time in epoch milliseconds.';

create index if not exists profiles_device_last_seen_idx
  on public.profiles (device_last_seen_at desc);

create index if not exists mini_users_last_seen_idx
  on public.mini_users (last_seen_at desc);

update public.mini_users as user_row
set
  nickname = coalesce(
    nullif(left(btrim(snapshot.payload #>> '{profile,nickname}'), 80), ''),
    user_row.nickname
  ),
  gender = coalesce(
    case
      when snapshot.payload #>> '{profile,gender}' in ('female', 'male', 'other', 'prefer_not')
        then snapshot.payload #>> '{profile,gender}'
      else null
    end,
    user_row.gender
  ),
  birth_date = coalesce(
    case
      when snapshot.payload #>> '{profile,birthDate}' ~ '^\d{4}-\d{2}-\d{2}$'
        then (snapshot.payload #>> '{profile,birthDate}')::date
      else null
    end,
    user_row.birth_date
  ),
  city = coalesce(
    nullif(left(btrim(snapshot.payload #>> '{profile,city}'), 80), ''),
    user_row.city
  ),
  updated_at = greatest(user_row.updated_at, snapshot.updated_at)
from public.mini_ledger_snapshots as snapshot
where snapshot.user_id = user_row.id;

update public.mini_users as user_row
set last_seen_at = greatest(
  coalesce(user_row.last_seen_at, 0),
  coalesce(session_stats.last_used_at, 0),
  coalesce(snapshot_stats.updated_at, 0),
  coalesce(user_row.updated_at, 0)
)
from (
  select user_id, max(last_used_at) as last_used_at
  from public.mini_sessions
  group by user_id
) as session_stats
full join (
  select user_id, max(updated_at) as updated_at
  from public.mini_ledger_snapshots
  group by user_id
) as snapshot_stats
on snapshot_stats.user_id = session_stats.user_id
where user_row.id = coalesce(session_stats.user_id, snapshot_stats.user_id);
