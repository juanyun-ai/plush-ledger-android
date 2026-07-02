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
const chinaOffsetMs = 8 * 60 * 60 * 1000;

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
    select(admin, "feedback", "id,user_id,email,content,status,source,page,app_version,created_at,updated_at", "created_at", false, 200),
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
    users: analytics.users,
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
    select(admin, "profiles", "id,display_name,phone,email,currency,role,membership_tier,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_users", "id,nickname,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_sessions", "user_id,created_at,last_used_at", "last_used_at", false, 1000),
    select(admin, "mini_ledger_snapshots", "user_id,payload,created_at,updated_at", "updated_at", false, 1000),
    select(admin, "transactions", "id,user_id,type,amount_minor,occurred_at,created_at,updated_at,deleted_at", "created_at", false, 2000),
    select(admin, "feedback", "id,user_id,status,source,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_feedback", "id,mini_user_id,status,created_at,updated_at", "created_at", false, 1000),
    select(admin, "app_versions", "version_code,version_name,apk_url,file_size_bytes,active,published_at,updated_at", "version_code", false, 20),
  ]);
  if (authUsersResult.error) throw new Error(`Unable to load auth users: ${authUsersResult.error.message}`);

  const authUsers = authUsersResult.data.users ?? [];
  const todayStart = startOfChinaDay(now);
  const todayKey = dateKeyChina(now);
  const liveTransactions = transactions.filter((row: Json) => !row.deleted_at);
  const appUsersTodayLogin = authUsers.filter((user) => dateMs(user.last_sign_in_at) >= todayStart).length;
  const activeAppUsers7 = authUsers.filter((user) => dateMs(user.last_sign_in_at) >= since7).length;
  const activeAppUsers30 = authUsers.filter((user) => dateMs(user.last_sign_in_at) >= since30).length;
  const appTodayRecords = liveTransactions.filter((row: Json) => numberValue(row.occurred_at) >= todayStart);
  const appTodaySyncRecords = liveTransactions.filter((row: Json) => numberValue(row.updated_at) >= todayStart);
  const appTodayRecordUsers = countDistinct(appTodayRecords, "user_id");
  const activeMiniUsers7 = countDistinct(miniSessions.filter((row: Json) => numberValue(row.last_used_at) >= since7), "user_id");
  const activeMiniUsers30 = countDistinct(miniSessions.filter((row: Json) => numberValue(row.last_used_at) >= since30), "user_id");
  const miniUsersTodayLogin = countDistinct(miniSessions.filter((row: Json) => numberValue(row.last_used_at) >= todayStart), "user_id");
  const miniTodayRecords = miniTransactionsFromSnapshots(snapshots).filter((item) => item.date === todayKey);
  const miniTodaySyncRecords = miniTransactionsFromSnapshots(snapshots).filter((item) => item.updated_at >= todayStart);
  const miniTodayRecordUsers = countDistinct(miniTodayRecords, "user_id");
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
      address: "2998319435@qq.com",
      source: "QQ 邮箱备用收件箱",
      connected_to_feedback: false,
      note: "当前 MX 不是可靠收件箱；后台主通道是 App 内在线留言写入 feedback 表，以及小程序 mini_feedback。",
    },
    summary: {
      auth_users: authUsers.length,
      app_profiles: profiles.length,
      mini_users: miniUsers.length,
      app_users: authUsers.length,
      total_known_users: authUsers.length + miniUsers.length,
      today_key: todayKey,
      app_today_logins: appUsersTodayLogin,
      mini_today_logins: miniUsersTodayLogin,
      app_today_records: appTodayRecords.length,
      app_today_record_users: appTodayRecordUsers,
      app_today_sync_records: appTodaySyncRecords.length,
      mini_today_records: miniTodayRecords.length,
      mini_today_record_users: miniTodayRecordUsers,
      mini_today_sync_records: miniTodaySyncRecords.length,
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
        ["App", aggregateAuthByDay(authUsers, "created_at", 14)],
        ["小程序", aggregateByDay(miniUsers, "created_at", 14)],
      ]),
      active_by_day: mergeSeries([
        ["App 登录", aggregateAuthByDay(authUsers, "last_sign_in_at", 14)],
        ["小程序", aggregateByDay(miniSessions, "last_used_at", 14, "user_id")],
      ]),
      transactions_by_day: mergeSeries([
        ["App", aggregateByDay(liveTransactions, "occurred_at", 14)],
        ["小程序", aggregateMiniTransactionsByDay(snapshots, 14)],
      ]),
      feedback_by_status: statusBuckets(feedbackRows),
    },
    activity: buildTodayActivity({
      todayKey,
      appUsersTodayLogin,
      miniUsersTodayLogin,
      appTodayRecords: appTodayRecords.length,
      appTodayRecordUsers,
      appTodaySyncRecords: appTodaySyncRecords.length,
      miniTodayRecords: miniTodayRecords.length,
      miniTodayRecordUsers,
      miniTodaySyncRecords: miniTodaySyncRecords.length,
    }),
    users: buildUserRows(authUsers, profiles, miniUsers, miniSessions, snapshots, liveTransactions, appFeedbackRows, miniFeedbackRows, todayStart, todayKey),
  };
}

function aggregateByDay(rows: Json[], field: string, days: number, distinctField?: string) {
  const start = startOfChinaDay(Date.now() - (days - 1) * dayMs);
  return Array.from({ length: days }, (_, index) => {
    const day = start + index * dayMs;
    const end = day + dayMs;
    const matches = rows.filter((row) => {
      const value = numberValue(row[field]);
      return value >= day && value < end;
    });
    return {
      date: monthDayKeyChina(day),
      value: distinctField ? countDistinct(matches, distinctField) : matches.length,
    };
  });
}

function aggregateAuthByDay(users: unknown[], field: string, days: number) {
  const rows = users.map((user) => ({ value_at: dateMs((user as Json)[field]) })).filter((row) => row.value_at > 0);
  return aggregateByDay(rows, "value_at", days);
}

function aggregateMiniTransactionsByDay(snapshots: Json[], days: number) {
  const start = startOfChinaDay(Date.now() - (days - 1) * dayMs);
  const rows = miniTransactionsFromSnapshots(snapshots);
  return Array.from({ length: days }, (_, index) => {
    const day = start + index * dayMs;
    const date = dateKeyChina(day);
    return {
      date: monthDayKeyChina(day),
      value: rows.filter((row) => row.date === date).length,
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

function buildTodayActivity(values: Record<string, number | string>) {
  return [
    ["统计日期", values.todayKey, "Asia/Shanghai 自然日"],
    ["App 今日登录用户", values.appUsersTodayLogin, "auth.users.last_sign_in_at"],
    ["小程序今日打开用户", values.miniUsersTodayLogin, "mini_sessions.last_used_at"],
    ["App 今日记账条数", values.appTodayRecords, "transactions.occurred_at"],
    ["App 今日记账用户", values.appTodayRecordUsers, "transactions.user_id"],
    ["App 今日同步账目", values.appTodaySyncRecords, "transactions.updated_at"],
    ["小程序今日记账条数", values.miniTodayRecords, "快照 transactions[].date"],
    ["小程序今日记账用户", values.miniTodayRecordUsers, "快照 user_id"],
    ["小程序今日同步账目", values.miniTodaySyncRecords, "快照 transactions[].updatedAt"],
  ].map(([label, value, evidence]) => ({ label, value, evidence }));
}

function buildUserRows(
  authUsers: Array<any>,
  profiles: Json[],
  miniUsers: Json[],
  miniSessions: Json[],
  snapshots: Json[],
  liveTransactions: Json[],
  appFeedbackRows: Json[],
  miniFeedbackRows: Json[],
  todayStart: number,
  todayKey: string,
) {
  const profilesById = mapBy(profiles, "id");
  const appTxStats = summarizeAppTransactions(liveTransactions, todayStart);
  const appFeedbackStats = summarizeFeedback(appFeedbackRows, "user_id");
  const miniSessionStats = summarizeMiniSessions(miniSessions, todayStart);
  const miniTxStats = summarizeMiniTransactions(snapshots, todayStart, todayKey);
  const miniFeedbackStats = summarizeFeedback(miniFeedbackRows, "mini_user_id");

  const appRows = authUsers.map((user) => {
    const profile = profilesById.get(user.id) ?? {};
    const metadata = user.user_metadata ?? {};
    const tx = appTxStats.get(user.id) ?? emptyUserStats();
    const feedback = appFeedbackStats.get(user.id) ?? emptyFeedbackStats();
    const registeredAt = numberValue(profile.created_at) || dateMs(user.created_at);
    const lastLoginAt = dateMs(user.last_sign_in_at);
    return {
      source: "App",
      user_id: user.id,
      display_name: stringValue(profile.display_name, 80) || stringValue(metadata.display_name, 80) || stringValue(user.email, 120) || shortId(user.id),
      email: stringValue(user.email, 160) || stringValue(profile.email, 160),
      contact: stringValue(profile.phone, 80),
      registered_at: registeredAt,
      last_login_at: lastLoginAt,
      today_logged_in: lastLoginAt >= todayStart,
      total_records: tx.total_records,
      today_records: tx.today_records,
      today_sync_records: tx.today_sync_records,
      last_record_at: tx.last_record_at,
      last_sync_at: tx.last_sync_at,
      feedback_count: feedback.feedback_count,
      last_feedback_at: feedback.last_feedback_at,
      role: stringValue(profile.role, 32) || "user",
      membership_tier: stringValue(profile.membership_tier, 32) || "free",
      evidence: "Auth 登录 + transactions 云端表",
    };
  });

  const miniRows = miniUsers.map((user: Json) => {
    const id = String(user.id);
    const sessions = miniSessionStats.get(id) ?? emptySessionStats();
    const tx = miniTxStats.get(id) ?? emptyUserStats();
    const feedback = miniFeedbackStats.get(id) ?? emptyFeedbackStats();
    return {
      source: "小程序",
      user_id: id,
      display_name: stringValue(user.nickname, 80) || `小程序用户 ${shortId(id)}`,
      email: "",
      contact: "",
      registered_at: numberValue(user.created_at),
      last_login_at: sessions.last_login_at,
      today_logged_in: sessions.today_logged_in,
      total_records: tx.total_records,
      today_records: tx.today_records,
      today_sync_records: tx.today_sync_records,
      last_record_at: tx.last_record_at,
      last_sync_at: tx.last_sync_at,
      feedback_count: feedback.feedback_count,
      last_feedback_at: feedback.last_feedback_at,
      role: "mini_user",
      membership_tier: "",
      evidence: "mini_sessions + mini_ledger_snapshots",
    };
  });

  return [...appRows, ...miniRows].sort((a, b) => {
    const aTime = Math.max(numberValue(a.last_login_at), numberValue(a.last_sync_at), numberValue(a.last_record_at), numberValue(a.registered_at));
    const bTime = Math.max(numberValue(b.last_login_at), numberValue(b.last_sync_at), numberValue(b.last_record_at), numberValue(b.registered_at));
    return bTime - aTime;
  });
}

function summarizeAppTransactions(rows: Json[], todayStart: number) {
  const stats = new Map<string, ReturnType<typeof emptyUserStats>>();
  for (const row of rows) {
    const id = String(row.user_id ?? "");
    if (!id) continue;
    const stat = stats.get(id) ?? emptyUserStats();
    stat.total_records += 1;
    if (numberValue(row.occurred_at) >= todayStart) stat.today_records += 1;
    if (numberValue(row.updated_at) >= todayStart) stat.today_sync_records += 1;
    stat.last_record_at = Math.max(stat.last_record_at, numberValue(row.occurred_at));
    stat.last_sync_at = Math.max(stat.last_sync_at, numberValue(row.updated_at));
    stats.set(id, stat);
  }
  return stats;
}

function summarizeMiniTransactions(snapshots: Json[], todayStart: number, todayKey: string) {
  const stats = new Map<string, ReturnType<typeof emptyUserStats>>();
  for (const row of miniTransactionsFromSnapshots(snapshots)) {
    const id = row.user_id;
    if (!id) continue;
    const stat = stats.get(id) ?? emptyUserStats();
    stat.total_records += 1;
    if (row.date === todayKey) stat.today_records += 1;
    if (row.updated_at >= todayStart) stat.today_sync_records += 1;
    stat.last_record_at = Math.max(stat.last_record_at, row.record_at);
    stat.last_sync_at = Math.max(stat.last_sync_at, row.updated_at);
    stats.set(id, stat);
  }
  return stats;
}

function miniTransactionsFromSnapshots(snapshots: Json[]) {
  const rows: Array<{ user_id: string; date: string; record_at: number; updated_at: number }> = [];
  for (const snapshot of snapshots) {
    const userId = String(snapshot.user_id ?? "");
    const payload = snapshot.payload && typeof snapshot.payload === "object" ? snapshot.payload as Json : {};
    const transactions = Array.isArray(payload.transactions) ? payload.transactions : [];
    for (const raw of transactions) {
      if (!raw || typeof raw !== "object") continue;
      const tx = raw as Json;
      const date = stringValue(tx.date, 10);
      rows.push({
        user_id: userId,
        date,
        record_at: date ? Date.parse(`${date}T00:00:00+08:00`) : numberValue(tx.createdAt),
        updated_at: numberValue(tx.updatedAt) || numberValue(tx.createdAt) || numberValue(snapshot.updated_at),
      });
    }
  }
  return rows;
}

function summarizeMiniSessions(rows: Json[], todayStart: number) {
  const stats = new Map<string, ReturnType<typeof emptySessionStats>>();
  for (const row of rows) {
    const id = String(row.user_id ?? "");
    if (!id) continue;
    const stat = stats.get(id) ?? emptySessionStats();
    const lastUsed = numberValue(row.last_used_at);
    stat.last_login_at = Math.max(stat.last_login_at, lastUsed);
    stat.today_logged_in = stat.today_logged_in || lastUsed >= todayStart;
    stats.set(id, stat);
  }
  return stats;
}

function summarizeFeedback(rows: Json[], field: string) {
  const stats = new Map<string, ReturnType<typeof emptyFeedbackStats>>();
  for (const row of rows) {
    const id = String(row[field] ?? "");
    if (!id) continue;
    const stat = stats.get(id) ?? emptyFeedbackStats();
    stat.feedback_count += 1;
    stat.last_feedback_at = Math.max(stat.last_feedback_at, numberValue(row.created_at));
    stats.set(id, stat);
  }
  return stats;
}

function emptyUserStats() {
  return { total_records: 0, today_records: 0, today_sync_records: 0, last_record_at: 0, last_sync_at: 0 };
}

function emptySessionStats() {
  return { last_login_at: 0, today_logged_in: false };
}

function emptyFeedbackStats() {
  return { feedback_count: 0, last_feedback_at: 0 };
}

function mapBy(rows: Json[], field: string) {
  return new Map(rows.map((row) => [String(row[field] ?? ""), row]).filter(([key]) => key));
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

function startOfChinaDay(value: number): number {
  return Math.floor((value + chinaOffsetMs) / dayMs) * dayMs - chinaOffsetMs;
}

function dateKeyChina(value: number): string {
  return new Date(value + chinaOffsetMs).toISOString().slice(0, 10);
}

function monthDayKeyChina(value: number): string {
  return new Date(value + chinaOffsetMs).toISOString().slice(5, 10);
}

function numberValue(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function dateMs(value: unknown): number {
  if (!value) return 0;
  const parsed = Date.parse(String(value));
  return Number.isFinite(parsed) ? parsed : 0;
}

function shortId(value: string): string {
  return value ? value.slice(0, 8) : "-";
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
      email: row.email || (row.source === "app_local" ? "本地模式用户" : ""),
      source_label: row.source === "app_local" ? "App 本地" : "App",
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
