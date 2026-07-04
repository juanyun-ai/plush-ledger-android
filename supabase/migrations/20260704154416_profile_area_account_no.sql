alter table public.profiles add column if not exists province text;
alter table public.profiles add column if not exists city text;
alter table public.profiles add column if not exists account_no text;
alter table public.profiles add column if not exists account_no_changed_month text;
alter table public.profiles add column if not exists account_no_changed_count integer not null default 0;

create unique index if not exists profiles_account_no_unique_idx
on public.profiles (lower(account_no))
where account_no is not null and btrim(account_no) <> '';

create or replace function public.profile_account_no_available(candidate text, current_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select not exists (
    select 1
    from public.profiles p
    where p.account_no is not null
      and lower(p.account_no) = lower(btrim(candidate))
      and p.id <> current_user_id
  );
$$;

revoke all on function public.profile_account_no_available(text, uuid) from public;
grant execute on function public.profile_account_no_available(text, uuid) to authenticated;
