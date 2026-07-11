insert into storage.buckets (
  id,
  name,
  public,
  file_size_limit,
  allowed_mime_types
)
values (
  'app-releases',
  'app-releases',
  true,
  104857600,
  array[
    'application/vnd.android.package-archive',
    'application/octet-stream'
  ]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
