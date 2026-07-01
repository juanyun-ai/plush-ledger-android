create table if not exists public.mini_feedback (
  id uuid primary key default gen_random_uuid(),
  mini_user_id uuid references public.mini_users(id) on delete set null,
  contact text,
  content text not null check (char_length(content) between 5 and 500),
  category text not null default 'suggestion' check (category in ('bug', 'suggestion', 'data', 'other')),
  status text not null default 'new' check (status in ('new', 'triaged', 'done', 'ignored')),
  source text not null default 'mini_program',
  page text,
  app_version text,
  client_info jsonb not null default '{}'::jsonb,
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

create index if not exists mini_feedback_status_idx on public.mini_feedback(status, created_at desc);
create index if not exists mini_feedback_user_idx on public.mini_feedback(mini_user_id, created_at desc);

alter table public.mini_feedback enable row level security;

revoke all on public.mini_feedback from anon, authenticated;
grant select, insert, update, delete on public.mini_feedback to service_role;
