import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const jsonHeaders = { "Content-Type": "application/json; charset=utf-8" };

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: jsonHeaders,
    });
  }

  const authorization = request.headers.get("Authorization");
  if (!authorization) {
    return new Response(JSON.stringify({ error: "Unauthorized" }), {
      status: 401,
      headers: jsonHeaders,
    });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonKey || !serviceRoleKey) {
    return new Response(JSON.stringify({ error: "Server is not configured" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }

  const userClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const {
    data: { user },
    error: userError,
  } = await userClient.auth.getUser();

  if (userError || !user) {
    return new Response(JSON.stringify({ error: "Invalid session" }), {
      status: 401,
      headers: jsonHeaders,
    });
  }

  const admin = createClient(supabaseUrl, serviceRoleKey);
  const ownedTables = [
    "membership_orders",
    "feedback",
    "budgets",
    "transactions",
    "categories",
    "accounts",
    "books",
  ];

  for (const table of ownedTables) {
    const { error } = await admin.from(table).delete().eq("user_id", user.id);
    if (error) {
      return new Response(JSON.stringify({ error: `Unable to clear ${table}` }), {
        status: 500,
        headers: jsonHeaders,
      });
    }
  }

  const { error: profileError } = await admin
    .from("profiles")
    .delete()
    .eq("id", user.id);
  if (profileError) {
    return new Response(JSON.stringify({ error: "Unable to clear profile" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }

  const { error: authError } = await admin.auth.admin.deleteUser(user.id);
  if (authError) {
    return new Response(JSON.stringify({ error: "Unable to delete account" }), {
      status: 500,
      headers: jsonHeaders,
    });
  }

  return new Response(JSON.stringify({ success: true }), {
    status: 200,
    headers: jsonHeaders,
  });
});
