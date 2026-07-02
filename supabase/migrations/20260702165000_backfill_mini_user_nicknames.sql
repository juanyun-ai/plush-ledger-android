update public.mini_users as user_row
set
  nickname = left(btrim(snapshot.payload #>> '{profile,nickname}'), 80),
  updated_at = greatest(user_row.updated_at, snapshot.updated_at)
from public.mini_ledger_snapshots as snapshot
where snapshot.user_id = user_row.id
  and nullif(btrim(snapshot.payload #>> '{profile,nickname}'), '') is not null
  and btrim(snapshot.payload #>> '{profile,nickname}') <> '绒绒用户'
  and (
    user_row.nickname is null
    or btrim(user_row.nickname) = ''
    or user_row.nickname = '绒绒用户'
  );
