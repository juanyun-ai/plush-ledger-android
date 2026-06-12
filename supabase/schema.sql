create extension if not exists "pgcrypto";

create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  display_name text not null,
  phone text,
  email text,
  currency text not null default 'CNY',
  created_at bigint not null,
  updated_at bigint not null
);

create table if not exists public.books (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  name text not null,
  currency text not null default 'CNY',
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists public.accounts (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  book_id uuid not null references public.books(id) on delete cascade,
  name text not null,
  kind text not null,
  color_hex text not null,
  initial_balance_minor bigint not null default 0,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists public.categories (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  book_id uuid not null references public.books(id) on delete cascade,
  name text not null,
  kind text not null check (kind in ('expense', 'income')),
  color_hex text not null,
  icon text not null,
  sort_order integer not null default 0,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists public.transactions (
  id uuid primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  book_id uuid not null references public.books(id) on delete cascade,
  type text not null check (type in ('expense', 'income', 'transfer')),
  amount_minor bigint not null check (amount_minor > 0),
  currency text not null default 'CNY',
  category_id uuid references public.categories(id),
  account_id uuid not null references public.accounts(id),
  to_account_id uuid references public.accounts(id),
  note text not null default '',
  occurred_at bigint not null,
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create table if not exists public.budgets (
  id text primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  book_id uuid not null references public.books(id) on delete cascade,
  month text not null,
  category_id uuid references public.categories(id),
  limit_minor bigint not null check (limit_minor > 0),
  created_at bigint not null,
  updated_at bigint not null,
  deleted_at bigint
);

create index if not exists books_user_idx on public.books(user_id);
create index if not exists accounts_user_book_idx on public.accounts(user_id, book_id);
create index if not exists categories_user_book_idx on public.categories(user_id, book_id);
create index if not exists transactions_user_book_time_idx on public.transactions(user_id, book_id, occurred_at desc);
create index if not exists budgets_user_month_idx on public.budgets(user_id, month);

alter table public.profiles enable row level security;
alter table public.books enable row level security;
alter table public.accounts enable row level security;
alter table public.categories enable row level security;
alter table public.transactions enable row level security;
alter table public.budgets enable row level security;

create policy "profiles_select_own" on public.profiles for select using (auth.uid() = id);
create policy "profiles_insert_own" on public.profiles for insert with check (auth.uid() = id);
create policy "profiles_update_own" on public.profiles for update using (auth.uid() = id) with check (auth.uid() = id);
create policy "profiles_delete_own" on public.profiles for delete using (auth.uid() = id);

create policy "books_own_all" on public.books for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "accounts_own_all" on public.accounts for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "categories_own_all" on public.categories for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "transactions_own_all" on public.transactions for all using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "budgets_own_all" on public.budgets for all using (auth.uid() = user_id) with check (auth.uid() = user_id);

grant usage on schema public to authenticated;
grant select, insert, update, delete on
  public.profiles,
  public.books,
  public.accounts,
  public.categories,
  public.transactions,
  public.budgets
to authenticated;
