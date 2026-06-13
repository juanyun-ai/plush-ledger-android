insert into public.official_messages (title, body, source_key, created_at)
values (
  '绒绒记账 0.7.0 已发布',
  '新版按参考图重做首页、账单、统计、记账和我的页面；修复头像切页闪烁；新增年龄、生日、性别资料，以及邮箱和手机号换绑入口。',
  'release:android:70',
  (extract(epoch from now()) * 1000)::bigint
)
on conflict (source_key) where source_key is not null do update set
  title = excluded.title,
  body = excluded.body,
  created_at = excluded.created_at;
