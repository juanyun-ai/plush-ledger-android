alter table public.categories
  add column if not exists parent_id uuid;

create index if not exists categories_parent_idx
  on public.categories(user_id, book_id, parent_id, sort_order)
  where deleted_at is null;

comment on column public.categories.parent_id is
  'Optional parent category id. Null denotes a top-level category.';
