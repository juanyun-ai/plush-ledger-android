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
const dayMs = 86_400_000;

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
  const [appFeedback, miniFeedback, messages, versions, config, profile, analytics] = await Promise.all([
    select(admin, "feedback", "id,user_id,email,content,status,created_at,updated_at", "created_at", false, 200),
    select(admin, "mini_feedback", "id,mini_user_id,contact,content,category,status,source,page,app_version,created_at,updated_at", "created_at", false, 200),
    select(admin, "official_messages", "id,title,body,source_key,created_at,updated_at", "created_at", false, 100),
    select(admin, "app_versions", "id,platform,version_code,version_name,apk_url,backup_apk_url,sha256,file_size_bytes,release_notes,is_mandatory,active,published_at,created_at,updated_at", "version_code", false, 60),
    select(admin, "app_config", "key,value,description,active,updated_at", "updated_at", false, 100),
    admin.from("profiles").select("id,display_name,email,role,membership_tier").eq("id", userId).maybeSingle(),
    loadAnalytics(admin),
  ]);
  return {
    admin: profile.data ?? null,
    analytics,
    feedback: normalizeFeedback(appFeedback, miniFeedback),
    messages,
    versions,
    config,
  };
}

async function loadAnalytics(admin: ReturnType<typeof createClient>) {
  const now = Date.now();
  const since30 = now - 30 * dayMs;
  const since7 = now - 7 * dayMs;
  const [
    authUsersResult,
    profiles,
    miniUsers,
    miniSessions,
    snapshots,
    transactions,
    appFeedbackRows,
    miniFeedbackRows,
    versions,
  ] = await Promise.all([
    admin.auth.admin.listUsers({ page: 1, perPage: 1000 }),
    select(admin, "profiles", "id,display_name,email,membership_tier,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_users", "id,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_sessions", "user_id,created_at,last_used_at", "last_used_at", false, 1000),
    select(admin, "mini_ledger_snapshots", "user_id,created_at,updated_at", "updated_at", false, 1000),
    select(admin, "transactions", "id,user_id,type,amount_minor,occurred_at,created_at,updated_at,deleted_at", "created_at", false, 2000),
    select(admin, "feedback", "id,status,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_feedback", "id,status,created_at,updated_at", "created_at", false, 1000),
    select(admin, "app_versions", "version_code,version_name,apk_url,file_size_bytes,active,published_at,updated_at", "version_code", false, 20),
  ]);
  if (authUsersResult.error) throw new Error(`Unable to load auth users: ${authUsersResult.error.message}`);

  const authUsers = authUsersResult.data.users ?? [];
  const liveTransactions = transactions.filter((row: Json) => !row.deleted_at);
  const activeAppUsers7 = countDistinct(liveTransactions.filter((row: Json) => numberValue(row.updated_at) >= since7), "user_id");
  const activeAppUsers30 = countDistinct(liveTransactions.filter((row: Json) => numberValue(row.updated_at) >= since30), "user_id");
  const activeMiniUsers7 = countDistinct(miniSessions.filter((row: Json) => numberValue(row.last_used_at) >= since7), "user_id");
  const activeMiniUsers30 = countDistinct(miniSessions.filter((row: Json) => numberValue(row.last_used_at) >= since30), "user_id");
  const expenseMinor = liveTransactions
    .filter((row: Json) => row.type === "expense")
    .reduce((sum: number, row: Json) => sum + numberValue(row.amount_minor), 0);
  const incomeMinor = liveTransactions
    .filter((row: Json) => row.type === "income")
    .reduce((sum: number, row: Json) => sum + numberValue(row.amount_minor), 0);
  const latestVersion = versions.find((row: Json) => row.active !== false) ?? versions[0] ?? null;
  const latestVersionSize = latestVersion ? numberValue(latestVersion.file_size_bytes) : 0;
  const feedbackRows = [...appFeedbackRows, ...miniFeedbackRows];

  return {
    refreshed_at: now,
    support_email: {
      address: "support@xiaoxing.online",
      source: "阿里云邮箱 MX",
      connected_to_feedback: false,
      note: "邮件会进入邮箱，不会自动写入 Supabase feedback 表；App 内反馈才会出现在这里。",
    },
    summary: {
      auth_users: authUsers.length,
      app_profiles: profiles.length,
      mini_users: miniUsers.length,
      total_known_users: profiles.length + miniUsers.length,
      app_active_7d: activeAppUsers7,
      app_active_30d: activeAppUsers30,
      mini_active_7d: activeMiniUsers7,
      mini_active_30d: activeMiniUsers30,
      transactions: liveTransactions.length,
      expense_minor: expenseMinor,
      income_minor: incomeMinor,
      feedback_total: feedbackRows.length,
      app_feedback_total: appFeedbackRows.length,
      mini_feedback_total: miniFeedbackRows.length,
      feedback_new: feedbackRows.filter((row: Json) => row.status === "new").length,
      feedback_done: feedbackRows.filter((row: Json) => row.status === "done").length,
      latest_version_name: stringValue(latestVersion?.version_name, 32),
      latest_version_code: numberValue(latestVersion?.version_code),
      latest_version_size_mb: latestVersionSize ? Number((latestVersionSize / 1024 / 1024).toFixed(2)) : 0,
    },
    charts: {
      signups_by_day: mergeSeries([
        ["App", aggregateByDay(profiles, "created_at", 14)],
        ["小程序", aggregateByDay(miniUsers, "created_at", 14)],
      ]),
      active_by_day: mergeSeries([
        ["App 账目", aggregateByDay(liveTransactions, "updated_at", 14, "user_id")],
        ["小程序", aggregateByDay(miniSessions, "last_used_at", 14, "user_id")],
      ]),
      transactions_by_day: aggregateByDay(liveTransactions, "occurred_at", 14),
      feedback_by_status: statusBuckets(feedbackRows),
      retention_cohorts: retentionCohorts(authUsers, profiles, miniUsers, miniSessions, snapshots, now),
    },
  };
}

function aggregateByDay(rows: Json[], field: string, days: number, distinctField?: string) {
  const start = startOfDay(Date.now() - (days - 1) * dayMs);
  return Array.from({ length: days }, (_, index) => {
    const day = start + index * dayMs;
    const end = day + dayMs;
    const matches = rows.filter((row) => {
      const value = numberValue(row[field]);
      return value >= day && value < end;
    });
    return {
      date: dayKey(day),
      value: distinctField ? countDistinct(matches, distinctField) : matches.length,
    };
  });
}

function mergeSeries(series: Array<[string, Array<{ date: string; value: number }>]>) {
  const dates = Array.from(new Set(series.flatMap(([, rows]) => rows.map((row) => row.date)))).sort();
  return dates.map((date) => {
    const row: Json = { date };
    for (const [name, rows] of series) row[name] = rows.find((item) => item.date === date)?.value ?? 0;
    return row;
  });
}

function retentionCohorts(
  authUsers: Array<{ id: string; created_at?: string; last_sign_in_at?: string | null }>,
  profiles: Json[],
  miniUsers: Json[],
  miniSessions: Json[],
  snapshots: Json[],
  now: number,
) {
  const appLastActivity = new Map<string, number>();
  for (const profile of profiles) appLastActivity.set(String(profile.id), numberValue(profile.updated_at));
  for (const user of authUsers) {
    const lastSignIn = user.last_sign_in_at ? Date.parse(user.last_sign_in_at) : 0;
    appLastActivity.set(user.id, Math.max(appLastActivity.get(user.id) ?? 0, lastSignIn));
  }

  const miniLastActivity = new Map<string, number>();
  for (const session of miniSessions) {
    const id = String(session.user_id);
    miniLastActivity.set(id, Math.max(miniLastActivity.get(id) ?? 0, numberValue(session.last_used_at)));
  }
  for (const snapshot of snapshots) {
    const id = String(snapshot.user_id);
    miniLastActivity.set(id, Math.max(miniLastActivity.get(id) ?? 0, numberValue(snapshot.updated_at)));
  }

  return [
    ...buildRetentionRows("App", authUsers.map((user) => ({
      id: user.id,
      created_at: user.created_at ? Date.parse(user.created_at) : 0,
      last_active_at: appLastActivity.get(user.id) ?? 0,
    })), now),
    ...buildRetentionRows("小程序", miniUsers.map((user: Json) => ({
      id: String(user.id),
      created_at: numberValue(user.created_at),
      last_active_at: miniLastActivity.get(String(user.id)) ?? numberValue(user.updated_at),
    })), now),
  ].slice(0, 10);
}

function buildRetentionRows(channel: string, users: Array<{ id: string; created_at: number; last_active_at: number }>, now: number) {
  const groups = new Map<string, Array<{ id: string; created_at: number; last_active_at: number }>>();
  for (const user of users) {
    if (!user.created_at) continue;
    const label = weekKey(user.created_at);
    groups.set(label, [...(groups.get(label) ?? []), user]);
  }
  return Array.from(groups.entries()).sort((a, b) => b[0].localeCompare(a[0])).map(([cohort, members]) => {
    const retained7 = members.filter((user) => user.last_active_at >= user.created_at + 7 * dayMs).length;
    const activeNow = members.filter((user) => user.last_active_at >= now - 7 * dayMs).length;
    return {
      channel,
      cohort,
      users: members.length,
      retained_7d: retained7,
      retained_7d_rate: members.length ? Math.round(retained7 / members.length * 100) : 0,
      active_7d: activeNow,
      active_7d_rate: members.length ? Math.round(activeNow / members.length * 100) : 0,
    };
  });
}

function statusBuckets(rows: Json[]) {
  return ["new", "triaged", "done", "ignored"].map((status) => ({
    status,
    value: rows.filter((row) => row.status === status).length,
  }));
}

function countDistinct(rows: Json[], field: string): number {
  return new Set(rows.map((row) => String(row[field] ?? "")).filter(Boolean)).size;
}

function startOfDay(value: number): number {
  const date = new Date(value);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function dayKey(value: number): string {
  return new Date(value).toISOString().slice(5, 10);
}

function weekKey(value: number): string {
  const date = new Date(startOfDay(value));
  const day = date.getDay() || 7;
  date.setDate(date.getDate() - day + 1);
  return date.toISOString().slice(0, 10);
}

function numberValue(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
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
  if (id.startsWith("mini:")) {
    const miniId = id.slice(5);
    const { error } = await admin
      .from("mini_feedback")
      .update({ status, updated_at: Date.now() })
      .eq("id", miniId);
    if (error) throw new Error(`Unable to update mini feedback: ${error.message}`);
    return { ok: true };
  }
  const { error } = await admin
    .from("feedback")
    .update({ status, updated_at: Date.now() })
    .eq("id", id);
  if (error) throw new Error(`Unable to update feedback: ${error.message}`);
  return { ok: true };
}

function normalizeFeedback(appRows: Json[], miniRows: Json[]) {
  const categoryLabel: Record<string, string> = {
    bug: "遇到问题",
    suggestion: "功能建议",
    data: "数据与同步",
    other: "其他",
  };
  return [
    ...appRows.map((row) => ({
      ...row,
      source_label: "App",
      contact: row.email || "",
    })),
    ...miniRows.map((row) => ({
      id: `mini:${row.id}`,
      user_id: row.mini_user_id,
      email: row.contact || "小程序用户",
      contact: row.contact || "",
      content: `[${categoryLabel[String(row.category)] || "小程序反馈"}] ${row.content || ""}`,
      status: row.status,
      source_label: "小程序",
      created_at: row.created_at,
      updated_at: row.updated_at,
      page: row.page,
      app_version: row.app_version,
    })),
  ].sort((a, b) => numberValue(b.created_at) - numberValue(a.created_at));
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
