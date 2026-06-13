alter table public.profiles
  add column if not exists age integer,
  add column if not exists birth_date date,
  add column if not exists gender text;

alter table public.profiles
  drop constraint if exists profiles_age_check,
  add constraint profiles_age_check check (age is null or (age between 0 and 150)),
  drop constraint if exists profiles_gender_check,
  add constraint profiles_gender_check check (
    gender is null or gender in ('female', 'male', 'other', 'prefer_not')
  );
