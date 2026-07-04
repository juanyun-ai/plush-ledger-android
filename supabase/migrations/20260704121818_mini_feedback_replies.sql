alter table public.mini_feedback
  add column if not exists developer_reply text,
  add column if not exists replied_at bigint,
  add column if not exists reply_seen_at bigint;

alter table public.feedback
  add column if not exists developer_reply text,
  add column if not exists replied_at bigint,
  add column if not exists reply_seen_at bigint;

create index if not exists mini_feedback_replied_idx
on public.mini_feedback(mini_user_id, replied_at desc)
where developer_reply is not null;

create index if not exists feedback_replied_idx
on public.feedback(user_id, replied_at desc)
where developer_reply is not null;
