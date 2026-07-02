import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type Json = Record<string, unknown>;

Deno.serve(async (request: Request) => {
  if (request.method === "OPTIONS") return json({ ok: true });
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !anonKey || !serviceRoleKey) return json({ error: "反馈服务暂未配置" }, 500);

  const body = await safeJson(request);
  const content = stringValue(body.content, 500);
  if (content.length < 5) return json({ error: "留言内容至少 5 个字" }, 400);

  const token = bearerToken(request);
  const user = token && token !== anonKey ? await getUser(supabaseUrl, anonKey, token) : null;
  const contact = stringValue(body.contact, 120) || null;
  const clientInfo = body.client && typeof body.client === "object" ? body.client as Json : {};
  const now = Date.now();

  const admin = createClient(supabaseUrl, serviceRoleKey);
  const { error } = await admin.from("feedback").insert({
    user_id: user?.id ?? null,
    email: user?.email ?? contact,
    content,
    status: "new",
    source: user ? "app" : "app_local",
    page: stringValue(body.page, 80) || "about",
    app_version: stringValue(body.appVersion, 32) || null,
    client_info: clientInfo,
    created_at: now,
    updated_at: now,
  });
  if (error) return json({ error: "留言保存失败，请稍后再试" }, 500);
  return json({ ok: true, createdAt: now });
});

async function getUser(supabaseUrl: string, anonKey: string, token: string): Promise<{ id: string; email: string | null } | null> {
  const client = createClient(supabaseUrl, anonKey);
  const { data, error } = await client.auth.getUser(token);
  if (error || !data.user) return null;
  return { id: data.user.id, email: data.user.email ?? null };
}

function bearerToken(request: Request): string {
  const authorization = request.headers.get("Authorization") ?? "";
  return authorization.replace(/^Bearer\s+/i, "").trim();
}

async function safeJson(request: Request): Promise<Json> {
  try {
    const value = await request.json();
    return value && typeof value === "object" ? value as Json : {};
  } catch {
    return {};
  }
}

function stringValue(value: unknown, maxLength: number): string {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function json(payload: Json, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}
