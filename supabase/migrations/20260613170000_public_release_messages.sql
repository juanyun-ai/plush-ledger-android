drop policy if exists official_messages_authenticated_read on public.official_messages;
drop policy if exists official_messages_public_read on public.official_messages;

create policy official_messages_public_read
on public.official_messages for select to anon, authenticated
using (true);

grant select on public.official_messages to anon, authenticated;
