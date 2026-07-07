drop index if exists public.diary_entries_user_date_idx;

create index if not exists diary_entries_user_date_idx
  on public.diary_entries(user_id, date);
