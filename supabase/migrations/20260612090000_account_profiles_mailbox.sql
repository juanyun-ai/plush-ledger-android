alter table public.profiles add column if not exists avatar_key text not null default 'sunny';
alter table public.profiles add column if not exists role text not null default 'user';
alter table public.profiles add column if not exists membership_tier text not null default 'free';
alter table public.profiles add column if not exists wechat_bound boolean not null default false;
alter table public.profiles add column if not exists qq_bound boolean not null default false;
alter table public.profiles add column if not exists agreement_version text;
alter table public.profiles add column if not exists agreed_at bigint;

create or replace function public.protect_profile_entitlements()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if auth.uid() is not null then
    if tg_op = 'INSERT' then
      new.role := 'user';
      new.membership_tier := 'free';
    elsif new.role is distinct from old.role
       or new.membership_tier is distinct from old.membership_tier then
      raise exception 'role and membership are server-managed';
    end if;
  end if;
  return new;
end;
$$;

drop trigger if exists protect_profile_entitlements_trigger on public.profiles;
create trigger protect_profile_entitlements_trigger
before insert or update on public.profiles
for each row execute function public.protect_profile_entitlements();

create table if not exists public.official_messages (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  body text not null,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

create table if not exists public.feedback (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  email text,
  content text not null check (char_length(content) between 5 and 500),
  status text not null default 'new',
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

create table if not exists public.membership_orders (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider text not null check (provider in ('wechat', 'alipay')),
  amount_minor bigint not null default 1 check (amount_minor = 1),
  status text not null default 'pending' check (status in ('pending', 'paid', 'closed', 'refunded')),
  provider_order_id text,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

alter table public.official_messages enable row level security;
alter table public.feedback enable row level security;
alter table public.membership_orders enable row level security;

drop policy if exists official_messages_authenticated_read on public.official_messages;
create policy official_messages_authenticated_read
on public.official_messages for select to authenticated
using (true);

drop policy if exists feedback_insert_own on public.feedback;
create policy feedback_insert_own
on public.feedback for insert to authenticated
with check (auth.uid() = user_id);

drop policy if exists feedback_select_own on public.feedback;
create policy feedback_select_own
on public.feedback for select to authenticated
using (auth.uid() = user_id);

drop policy if exists membership_orders_insert_own on public.membership_orders;
create policy membership_orders_insert_own
on public.membership_orders for insert to authenticated
with check (auth.uid() = user_id and status = 'pending');

drop policy if exists membership_orders_select_own on public.membership_orders;
create policy membership_orders_select_own
on public.membership_orders for select to authenticated
using (auth.uid() = user_id);

grant usage on schema public to authenticated;
grant select on public.official_messages to authenticated;
grant select, insert on public.feedback to authenticated;
grant select, insert on public.membership_orders to authenticated;

insert into public.official_messages (id, title, body)
values (
  '75a81036-3746-4be2-b94f-43c96a68de72',
  '欢迎使用绒绒记账',
  '邮箱账号支持云端同步；本地模式的数据只保存在当前设备。重要服务变化会在这里通知。'
)
on conflict (id) do update set
  title = excluded.title,
  body = excluded.body,
  updated_at = ((extract(epoch from clock_timestamp()) * 1000)::bigint);
