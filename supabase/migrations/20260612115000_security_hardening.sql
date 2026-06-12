-- This function is only invoked by the profiles trigger. It must not be
-- callable as an exposed RPC by anonymous or authenticated clients.
revoke execute on function public.protect_profile_entitlements()
from public, anon, authenticated;
