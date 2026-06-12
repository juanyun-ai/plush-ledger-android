create table if not exists public.app_versions (
  id uuid primary key default gen_random_uuid(),
  platform text not null default 'android' check (platform = 'android'),
  version_code integer not null unique,
  version_name text not null,
  apk_url text not null,
  sha256 text not null,
  file_size_bytes bigint not null check (file_size_bytes > 0),
  release_notes text not null default '',
  is_mandatory boolean not null default false,
  active boolean not null default true,
  published_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  created_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint),
  updated_at bigint not null default ((extract(epoch from clock_timestamp()) * 1000)::bigint)
);

alter table public.app_versions enable row level security;

drop policy if exists app_versions_public_read on public.app_versions;
create policy app_versions_public_read
on public.app_versions for select to anon, authenticated
using (active = true);

grant select on public.app_versions to anon, authenticated;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', false, 5242880, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set
  public = false,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('app-releases', 'app-releases', true, 104857600, array['application/vnd.android.package-archive', 'application/octet-stream'])
on conflict (id) do update set
  public = true,
  file_size_limit = excluded.file_size_limit,
  allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists avatars_select_own on storage.objects;
create policy avatars_select_own
on storage.objects for select to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_insert_own on storage.objects;
create policy avatars_insert_own
on storage.objects for insert to authenticated
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_update_own on storage.objects;
create policy avatars_update_own
on storage.objects for update to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists avatars_delete_own on storage.objects;
create policy avatars_delete_own
on storage.objects for delete to authenticated
using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);
