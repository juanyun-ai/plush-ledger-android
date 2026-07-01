import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

type Json = Record<string, unknown>;

const defaultAllowedOrigins = new Set([
  "https://admin.xiaoxing.online",
  "https://rongrong-admin.pages.dev",
  "http://localhost:4177",
  "http://127.0.0.1:4177",
]);
const allowedStatuses = new Set(["new", "triaged", "done", "ignored"]);

Deno.serve(async (request) => {
  const corsHeaders = buildCorsHeaders(request);
  if (!corsHeaders) return json({ error: "Origin not allowed" }, 403, {});
  if (request.method === "OPTIONS") return json({ ok: true }, 200, corsHeaders);
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405, corsHeaders);

  try {
    const env = readEnv();
    const body = await safeJson(request);
    const { admin, user } = await requireAdmin(request, env);
    const action = stringValue(body.action, 80);

    if (action === "dashboard") return json(await dashboard(admin, user.id), 200, corsHeaders);
    if (action === "feedback.updateStatus") return json(await updateFeedbackStatus(admin, body), 200, corsHeaders);
    if (action === "message.upsert") return json(await upsertMessage(admin, body), 200, corsHeaders);
    if (action === "message.delete") return json(await deleteById(admin, "official_messages", body), 200, corsHeaders);
    if (action === "version.upsert") return json(await upsertVersion(admin, body), 200, corsHeaders);
    if (action === "config.upsert") return json(await upsertConfig(admin, body), 200, corsHeaders);
    if (action === "config.delete") return json(await deleteConfig(admin, body), 200, corsHeaders);

    return json({ error: "Unknown action" }, 400, corsHeaders);
  } catch (error) {
    const message = error instanceof Error ? error.message : "服务暂时不可用";
    const status = message.includes("Unauthorized") ? 401 : message.includes("Forbidden") ? 403 : 500;
    return json({ error: message }, status, corsHeaders);
  }
});

function readEnv() {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  const adminEmails = (Deno.env.get("ADMIN_EMAILS") ?? "")
    .split(",")
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean);
  if (!supabaseUrl || !anonKey || !serviceRoleKey) throw new Error("Server is not configured");
  return { supabaseUrl, anonKey, serviceRoleKey, adminEmails };
}

async function requireAdmin(request: Request, env: ReturnType<typeof readEnv>) {
  const authorization = request.headers.get("Authorization") ?? "";
  if (!authorization) throw new Error("Unauthorized");

  const userClient = createClient(env.supabaseUrl, env.anonKey, {
    global: { headers: { Authorization: authorization } },
  });
  const { data, error } = await userClient.auth.getUser();
  if (error || !data.user) throw new Error("Unauthorized");

  const admin = createClient(env.supabaseUrl, env.serviceRoleKey);
  const { data: profile, error: profileError } = await admin
    .from("profiles")
    .select("id, display_name, email, role, membership_tier")
    .eq("id", data.user.id)
    .maybeSingle();
  if (profileError) throw new Error("Unable to load admin profile");

  const email = String(data.user.email ?? profile?.email ?? "").toLowerCase();
  const isAdmin = profile?.role === "admin" || env.adminEmails.includes(email);
  if (!isAdmin) throw new Error("Forbidden: admin only");
  return { admin, user: data.user, profile };
}

async function dashboard(admin: ReturnType<typeof createClient>, userId: string) {
  const [feedback, messages, versions, config, profile] = await Promise.all([
    select(admin, "feedback", "id,user_id,email,content,status,created_at,updated_at", "created_at", false, 200),
    select(admin, "official_messages", "id,title,body,source_key,created_at,updated_at", "created_at", false, 100),
    select(admin, "app_versions", "id,platform,version_code,version_name,apk_url,backup_apk_url,sha256,file_size_bytes,release_notes,is_mandatory,active,published_at,created_at,updated_at", "version_code", false, 60),
    select(admin, "app_config", "key,value,description,active,updated_at", "updated_at", false, 100),
    admin.from("profiles").select("id,display_name,email,role,membership_tier").eq("id", userId).maybeSingle(),
  ]);
  return {
    admin: profile.data ?? null,
    feedback,
    messages,
    versions,
    config,
  };
}

async function select(
  admin: ReturnType<typeof createClient>,
  table: string,
  columns: string,
  order: string,
  ascending: boolean,
  limit: number,
) {
  const { data, error } = await admin
    .from(table)
    .select(columns)
    .order(order, { ascending })
    .limit(limit);
  if (error) throw new Error(`Unable to load ${table}: ${error.message}`);
  return data ?? [];
}

async function updateFeedbackStatus(admin: ReturnType<typeof createClient>, body: Json) {
  const id = stringValue(body.id, 80);
  const status = stringValue(body.status, 20);
  if (!id || !allowedStatuses.has(status)) throw new Error("Invalid feedback status");
  const { error } = await admin
    .from("feedback")
    .update({ status, updated_at: Date.now() })
    .eq("id", id);
  if (error) throw new Error(`Unable to update feedback: ${error.message}`);
  return { ok: true };
}

async function upsertMessage(admin: ReturnType<typeof createClient>, body: Json) {
  const id = stringValue(body.id, 80);
  const title = stringValue(body.title, 80);
  const messageBody = stringValue(body.body, 2000);
  const sourceKey = stringValue(body.source_key, 120) || null;
  if (!title || !messageBody) throw new Error("Message title and body are required");
  const payload: Json = {
    title,
    body: messageBody,
    source_key: sourceKey,
    updated_at: Date.now(),
  };
  if (id) payload.id = id;
  const { error } = await admin.from("official_messages").upsert(payload, { onConflict: "id" });
  if (error) throw new Error(`Unable to save message: ${error.message}`);
  return { ok: true };
}

async function deleteById(admin: ReturnType<typeof createClient>, table: string, body: Json) {
  const id = stringValue(body.id, 80);
  if (!id) throw new Error("Missing id");
  const { error } = await admin.from(table).delete().eq("id", id);
  if (error) throw new Error(`Unable to delete ${table}: ${error.message}`);
  return { ok: true };
}

async function upsertVersion(admin: ReturnType<typeof createClient>, body: Json) {
  const payload = {
    platform: "android",
    version_code: positiveInt(body.version_code, "version_code"),
    version_name: stringValue(body.version_name, 32),
    apk_url: urlValue(body.apk_url, "apk_url"),
    backup_apk_url: optionalUrlValue(body.backup_apk_url),
    sha256: sha256Value(body.sha256),
    file_size_bytes: positiveInt(body.file_size_bytes, "file_size_bytes"),
    release_notes: stringValue(body.release_notes, 3000),
    is_mandatory: Boolean(body.is_mandatory),
    active: body.active !== false,
    updated_at: Date.now(),
    published_at: Date.now(),
  };
  if (!payload.version_name) throw new Error("version_name is required");
  const { error } = await admin.from("app_versions").upsert(payload, { onConflict: "version_code" });
  if (error) throw new Error(`Unable to save app version: ${error.message}`);
  return { ok: true };
}

async function upsertConfig(admin: ReturnType<typeof createClient>, body: Json) {
  const key = stringValue(body.key, 80);
  if (!/^[a-z][a-z0-9_:-]{1,79}$/.test(key)) throw new Error("Invalid config key");
  const value = body.value;
  if (value === undefined || value === null || typeof value !== "object") throw new Error("Config value must be JSON");
  const payload = {
    key,
    value,
    description: stringValue(body.description, 200),
    active: body.active !== false,
    updated_at: Date.now(),
  };
  const { error } = await admin.from("app_config").upsert(payload, { onConflict: "key" });
  if (error) throw new Error(`Unable to save config: ${error.message}`);
  return { ok: true };
}

async function deleteConfig(admin: ReturnType<typeof createClient>, body: Json) {
  const key = stringValue(body.key, 80);
  if (!key) throw new Error("Missing config key");
  const { error } = await admin.from("app_config").delete().eq("key", key);
  if (error) throw new Error(`Unable to delete config: ${error.message}`);
  return { ok: true };
}

function buildCorsHeaders(request: Request): HeadersInit | null {
  const origin = request.headers.get("Origin");
  const extraAllowedOrigins = (Deno.env.get("ADMIN_ALLOWED_ORIGINS") ?? "")
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
  const allowedOrigins = new Set([...defaultAllowedOrigins, ...extraAllowedOrigins]);
  if (origin && !allowedOrigins.has(origin)) return null;
  const allowOrigin = origin || "https://admin.xiaoxing.online";
  return {
    "Access-Control-Allow-Origin": allowOrigin,
    "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Vary": "Origin",
  };
}

function json(payload: unknown, status = 200, headers: HeadersInit = {}): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...headers, "content-type": "application/json; charset=utf-8", "Cache-Control": "no-store" },
  });
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

function positiveInt(value: unknown, field: string): number {
  const number = Number(value);
  if (!Number.isSafeInteger(number) || number <= 0) throw new Error(`${field} must be a positive integer`);
  return number;
}

function urlValue(value: unknown, field: string): string {
  const raw = stringValue(value, 600);
  if (!/^https:\/\//i.test(raw)) throw new Error(`${field} must be an HTTPS URL`);
  return raw;
}

function optionalUrlValue(value: unknown): string | null {
  const raw = stringValue(value, 600);
  return raw ? urlValue(raw, "backup_apk_url") : null;
}

function sha256Value(value: unknown): string {
  const raw = stringValue(value, 64).toLowerCase();
  if (!/^[a-f0-9]{64}$/.test(raw)) throw new Error("sha256 must be 64 hex characters");
  return raw;
}
