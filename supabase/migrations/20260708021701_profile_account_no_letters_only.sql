alter table public.profiles
  add constraint profiles_account_no_letters_6_8_chk
  check (account_no is null or btrim(account_no) ~ '^[A-Za-z]{6,8}$')
  not valid;

create or replace function public.profile_account_no_available(candidate text, current_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select btrim(candidate) ~ '^[A-Za-z]{6,8}$'
    and not exists (
      select 1
      from public.profiles p
      where p.account_no is not null
        and lower(p.account_no) = lower(btrim(candidate))
        and p.id <> current_user_id
    );
$$;

revoke all on function public.profile_account_no_available(text, uuid) from public;
grant execute on function public.profile_account_no_available(text, uuid) to authenticated;
