create or replace function public.admin_ops_stats()
returns jsonb
language sql
security definer
set search_path = public, storage, pg_catalog
as $$
with database_usage as (
  select
    pg_database_size(current_database())::bigint as bytes,
    pg_size_pretty(pg_database_size(current_database())) as pretty
),
storage_usage as (
  select
    bucket_id,
    count(*)::bigint as object_count,
    coalesce(sum(coalesce((metadata ->> 'size')::bigint, 0)), 0)::bigint as bytes
  from storage.objects
  group by bucket_id
),
table_usage as (
  select
    schemaname,
    relname as table_name,
    pg_total_relation_size(format('%I.%I', schemaname, relname)::regclass)::bigint as bytes,
    pg_size_pretty(pg_total_relation_size(format('%I.%I', schemaname, relname)::regclass)) as pretty
  from pg_stat_user_tables
  where schemaname in ('public', 'auth', 'storage')
  order by bytes desc
  limit 20
)
select jsonb_build_object(
  'database',
  (
    select jsonb_build_object(
      'bytes', bytes,
      'pretty', pretty,
      'limit_bytes', 524288000
    )
    from database_usage
  ),
  'storage',
  coalesce(
    (
      select jsonb_agg(
        jsonb_build_object(
          'bucket_id', bucket_id,
          'object_count', object_count,
          'bytes', bytes,
          'pretty', pg_size_pretty(bytes)
        )
        order by bytes desc
      )
      from storage_usage
    ),
    '[]'::jsonb
  ),
  'tables',
  coalesce(
    (
      select jsonb_agg(
        jsonb_build_object(
          'schema', schemaname,
          'table', table_name,
          'bytes', bytes,
          'pretty', pretty
        )
        order by bytes desc
      )
      from table_usage
    ),
    '[]'::jsonb
  )
);
$$;

revoke all on function public.admin_ops_stats() from public;
revoke all on function public.admin_ops_stats() from anon;
revoke all on function public.admin_ops_stats() from authenticated;
grant execute on function public.admin_ops_stats() to service_role;
