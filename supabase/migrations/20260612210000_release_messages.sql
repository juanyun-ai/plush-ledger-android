alter table public.official_messages
  add column if not exists source_key text;

create unique index if not exists official_messages_source_key_idx
  on public.official_messages(source_key)
  where source_key is not null;

create or replace function public.publish_app_release_message()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  if new.active then
    insert into public.official_messages (title, body, source_key, created_at, updated_at)
    values (
      '绒绒记账 v' || new.version_name || ' 更新',
      coalesce(nullif(new.release_notes, ''), '修复问题并改进使用体验。'),
      'release:' || new.platform || ':' || new.version_code::text,
      new.published_at,
      ((extract(epoch from clock_timestamp()) * 1000)::bigint)
    )
    on conflict (source_key) where source_key is not null do update set
      title = excluded.title,
      body = excluded.body,
      updated_at = excluded.updated_at;
  end if;
  return new;
end;
$$;

drop trigger if exists publish_app_release_message_trigger on public.app_versions;
create trigger publish_app_release_message_trigger
after insert or update of version_name, release_notes, active, published_at
on public.app_versions
for each row execute function public.publish_app_release_message();

revoke all on function public.publish_app_release_message() from public, anon, authenticated;
