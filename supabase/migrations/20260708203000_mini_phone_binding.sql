alter table public.mini_users
  add column if not exists phone text;

comment on column public.mini_users.phone is 'Optional phone number bound through WeChat mini-program getPhoneNumber authorization.';
