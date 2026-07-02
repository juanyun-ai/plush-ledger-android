alter table public.feedback
  alter column user_id drop not null;

alter table public.feedback
  add column if not exists source text not null default 'app',
  add column if not exists page text,
  add column if not exists app_version text,
  add column if not exists client_info jsonb not null default '{}'::jsonb;

update public.feedback
set source = 'app'
where source is null or source = '';

create index if not exists feedback_source_created_idx
on public.feedback(source, created_at desc);

comment on column public.feedback.user_id is
  'Nullable so local-mode App feedback can be accepted through the feedback-submit Edge Function.';

comment on column public.feedback.source is
  'app for signed-in Android feedback, app_local for Android local-mode feedback.';
