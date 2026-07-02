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

    if (action === "dashboard") return json(await dashboard(admin, user.id, body), 200, corsHeaders);
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

async function dashboard(admin: ReturnType<typeof createClient>, userId: string, body: Json) {
  const [appFeedback, miniFeedback, messages, versions, config, profile, analytics] = await Promise.all([
    select(admin, "feedback", "id,user_id,email,content,status,source,page,app_version,created_at,updated_at", "created_at", false, 200),
    select(admin, "mini_feedback", "id,mini_user_id,contact,content,category,status,source,page,app_version,created_at,updated_at", "created_at", false, 200),
    select(admin, "official_messages", "id,title,body,source_key,created_at,updated_at", "created_at", false, 100),
    select(admin, "app_versions", "id,platform,version_code,version_name,apk_url,backup_apk_url,sha256,file_size_bytes,release_notes,is_mandatory,active,published_at,created_at,updated_at", "version_code", false, 60),
    select(admin, "app_config", "key,value,description,active,updated_at", "updated_at", false, 100),
    admin.from("profiles").select("id,display_name,email,role,membership_tier").eq("id", userId).maybeSingle(),
    loadAnalytics(admin, body),
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

async function loadAnalytics(admin: ReturnType<typeof createClient>, body: Json = {}) {
  const now = Date.now();
  const selectedDate = requestedDateKey(body.date, now);
  const selectedStart = dayStartFromKey(selectedDate);
  const selectedEnd = selectedStart + dayMs;
  const since30 = selectedEnd - 30 * dayMs;
  const since7 = selectedEnd - 7 * dayMs;
  const [
    authUsersResult,
    profiles,
    miniUsers,
    miniSessions,
    snapshots,
    appActivityEvents,
    transactions,
    appFeedbackRows,
    miniFeedbackRows,
    versions,
  ] = await Promise.all([
    admin.auth.admin.listUsers({ page: 1, perPage: 1000 }),
    select(admin, "profiles", "id,display_name,phone,email,currency,role,membership_tier,age,birth_date,gender,city,device_brand,device_model,device_platform,device_last_seen_at,app_version,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_users", "id,nickname,gender,birth_date,city,device_brand,device_model,device_platform,app_version,last_seen_at,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_sessions", "user_id,created_at,last_used_at", "last_used_at", false, 1000),
    select(admin, "mini_ledger_snapshots", "user_id,payload,created_at,updated_at", "updated_at", false, 1000),
    select(admin, "app_activity_events", "id,user_id,event_type,device_brand,device_model,device_platform,app_version,occurred_at,created_at", "occurred_at", false, 5000),
    select(admin, "transactions", "id,user_id,type,amount_minor,occurred_at,created_at,updated_at,deleted_at", "created_at", false, 2000),
    select(admin, "feedback", "id,user_id,status,source,created_at,updated_at", "created_at", false, 1000),
    select(admin, "mini_feedback", "id,mini_user_id,status,created_at,updated_at", "created_at", false, 1000),
    select(admin, "app_versions", "version_code,version_name,apk_url,file_size_bytes,active,published_at,updated_at", "version_code", false, 20),
  ]);
  if (authUsersResult.error) throw new Error(`Unable to load auth users: ${authUsersResult.error.message}`);

  const authUsers = authUsersResult.data.users ?? [];
  const todayKey = dateKeyChina(now);
  const liveTransactions = transactions.filter((row: Json) => !row.deleted_at);
  const appActivity = buildAppActivityEvents(authUsers, profiles, appActivityEvents);
  const miniActivity = buildMiniActivityEvents(miniUsers, miniSessions, snapshots);
  const appDayActivity = appActivity.filter((row) => inRange(row.occurred_at, selectedStart, selectedEnd));
  const miniDayActivity = miniActivity.filter((row) => inRange(row.occurred_at, selectedStart, selectedEnd));
  const appUsersDayLogin = authUsers.filter((user) => inRange(dateMs(user.last_sign_in_at), selectedStart, selectedEnd)).length;
  const appUsersDayDeviceSeen = countDistinct(
    profiles.filter((row: Json) => inRange(numberValue(row.device_last_seen_at), selectedStart, selectedEnd)),
    "id",
  );
  const appDayOpenEvents = appActivityEvents.filter((row: Json) => inRange(numberValue(row.occurred_at), selectedStart, selectedEnd));
  const activeAppUsers7 = countDistinct(appActivity.filter((row) => row.occurred_at >= since7 && row.occurred_at < selectedEnd), "user_id");
  const activeAppUsers30 = countDistinct(appActivity.filter((row) => row.occurred_at >= since30 && row.occurred_at < selectedEnd), "user_id");
  const activeMiniUsers7 = countDistinct(miniActivity.filter((row) => row.occurred_at >= since7 && row.occurred_at < selectedEnd), "user_id");
  const activeMiniUsers30 = countDistinct(miniActivity.filter((row) => row.occurred_at >= since30 && row.occurred_at < selectedEnd), "user_id");
  const appDayRecords = liveTransactions.filter((row: Json) => inRange(numberValue(row.occurred_at), selectedStart, selectedEnd));
  const appDaySyncRecords = liveTransactions.filter((row: Json) => inRange(numberValue(row.updated_at), selectedStart, selectedEnd));
  const appDayRecordUsers = countDistinct(appDayRecords, "user_id");
  const miniTransactions = miniTransactionsFromSnapshots(snapshots);
  const miniDayRecords = miniTransactions.filter((item) => item.date === selectedDate);
  const miniDaySyncRecords = miniTransactions.filter((item) => inRange(item.updated_at, selectedStart, selectedEnd));
  const miniDayRecordUsers = countDistinct(miniDayRecords, "user_id");
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
      selected_date: selectedDate,
      selected_is_today: selectedDate === todayKey,
      app_day_active_users: countDistinct(appDayActivity, "user_id"),
      app_day_open_events: appDayOpenEvents.length,
      app_day_auth_logins: appUsersDayLogin,
      app_day_device_seen_users: appUsersDayDeviceSeen,
      mini_day_active_users: countDistinct(miniDayActivity, "user_id"),
      mini_day_session_users: countDistinct(
        miniSessions.filter((row: Json) => inRange(numberValue(row.last_used_at), selectedStart, selectedEnd)),
        "user_id",
      ),
      app_day_records: appDayRecords.length,
      app_day_record_users: appDayRecordUsers,
      app_day_sync_records: appDaySyncRecords.length,
      mini_day_records: miniDayRecords.length,
      mini_day_record_users: miniDayRecordUsers,
      mini_day_sync_records: miniDaySyncRecords.length,
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
        ["App", aggregateAuthByDay(authUsers, "created_at", 14, selectedStart)],
        ["小程序", aggregateByDay(miniUsers, "created_at", 14, selectedStart)],
      ]),
      active_by_day: mergeSeries([
        ["App 可证活跃", aggregateEventObjectsByDay(appActivity, 14, selectedStart, "user_id")],
        ["小程序可证活跃", aggregateEventObjectsByDay(miniActivity, 14, selectedStart, "user_id")],
      ]),
      transactions_by_day: mergeSeries([
        ["App", aggregateByDay(liveTransactions, "occurred_at", 14, selectedStart)],
        ["小程序", aggregateMiniTransactionsByDay(snapshots, 14, selectedStart)],
      ]),
      feedback_by_status: statusBuckets(feedbackRows),
    },
    activity: buildTodayActivity({
      selectedDate,
      appActiveUsers: countDistinct(appDayActivity, "user_id"),
      appOpenEvents: appDayOpenEvents.length,
      appAuthLogins: appUsersDayLogin,
      appDeviceSeenUsers: appUsersDayDeviceSeen,
      miniActiveUsers: countDistinct(miniDayActivity, "user_id"),
      miniSessionUsers: countDistinct(miniSessions.filter((row: Json) => inRange(numberValue(row.last_used_at), selectedStart, selectedEnd)), "user_id"),
      appDayRecords: appDayRecords.length,
      appDayRecordUsers,
      appDaySyncRecords: appDaySyncRecords.length,
      miniDayRecords: miniDayRecords.length,
      miniDayRecordUsers,
      miniDaySyncRecords: miniDaySyncRecords.length,
    }),
    users: buildUserRows(
      authUsers,
      profiles,
      miniUsers,
      miniSessions,
      snapshots,
      appActivityEvents,
      liveTransactions,
      appFeedbackRows,
      miniFeedbackRows,
      selectedStart,
      selectedDate,
    ),
  };
}

function aggregateByDay(rows: Json[], field: string, days: number, endDayStart: number, distinctField?: string) {
  const start = endDayStart - (days - 1) * dayMs;
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

function aggregateAuthByDay(users: unknown[], field: string, days: number, endDayStart: number) {
  const rows = users.map((user) => ({ value_at: dateMs((user as Json)[field]) })).filter((row) => row.value_at > 0);
  return aggregateByDay(rows, "value_at", days, endDayStart);
}

function aggregateMiniTransactionsByDay(snapshots: Json[], days: number, endDayStart: number) {
  const start = endDayStart - (days - 1) * dayMs;
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

function aggregateEventObjectsByDay(
  rows: Array<{ user_id: string; occurred_at: number }>,
  days: number,
  endDayStart: number,
  distinctField?: "user_id",
) {
  const start = endDayStart - (days - 1) * dayMs;
  return Array.from({ length: days }, (_, index) => {
    const day = start + index * dayMs;
    const end = day + dayMs;
    const matches = rows.filter((row) => inRange(row.occurred_at, day, end));
    return {
      date: monthDayKeyChina(day),
      value: distinctField ? new Set(matches.map((row) => row[distinctField]).filter(Boolean)).size : matches.length,
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
    ["统计日期", values.selectedDate, "Asia/Shanghai 自然日"],
    ["App 可证活跃用户", values.appActiveUsers, "app_activity_events / auth.users.last_sign_in_at / profiles.device_last_seen_at"],
    ["App 打开事件", values.appOpenEvents, "app_activity_events.occurred_at；旧版本没有该事件表数据"],
    ["App Auth 登录用户", values.appAuthLogins, "auth.users.last_sign_in_at；不等于 App 打开次数"],
    ["App 设备同步用户", values.appDeviceSeenUsers, "profiles.device_last_seen_at；仅同步时更新"],
    ["小程序可证活跃用户", values.miniActiveUsers, "mini_sessions.last_used_at / mini_users.last_seen_at / 快照 updated_at"],
    ["小程序 session 用户", values.miniSessionUsers, "mini_sessions.last_used_at"],
    ["App 选日发生记账", values.appDayRecords, "transactions.occurred_at"],
    ["App 选日记账用户", values.appDayRecordUsers, "transactions.user_id"],
    ["App 选日同步账目", values.appDaySyncRecords, "transactions.updated_at"],
    ["小程序选日发生记账", values.miniDayRecords, "快照 transactions[].date"],
    ["小程序选日记账用户", values.miniDayRecordUsers, "快照 user_id"],
    ["小程序选日同步账目", values.miniDaySyncRecords, "快照 transactions[].updatedAt"],
  ].map(([label, value, evidence]) => ({ label, value, evidence }));
}

function buildAppActivityEvents(authUsers: Array<any>, profiles: Json[], appActivityEvents: Json[]) {
  const events: Array<{ user_id: string; occurred_at: number; type: string }> = [];
  for (const row of appActivityEvents) {
    const userId = String(row.user_id ?? "");
    const occurredAt = numberValue(row.occurred_at);
    if (userId && occurredAt) events.push({ user_id: userId, occurred_at: occurredAt, type: stringValue(row.event_type, 40) || "app_open" });
  }
  for (const user of authUsers) {
    const occurredAt = dateMs(user.last_sign_in_at);
    if (user.id && occurredAt) events.push({ user_id: user.id, occurred_at: occurredAt, type: "auth_last_sign_in" });
  }
  for (const profile of profiles) {
    const userId = String(profile.id ?? "");
    const occurredAt = numberValue(profile.device_last_seen_at);
    if (userId && occurredAt) events.push({ user_id: userId, occurred_at: occurredAt, type: "profile_device_seen" });
  }
  return events;
}

function buildMiniActivityEvents(miniUsers: Json[], miniSessions: Json[], snapshots: Json[]) {
  const events: Array<{ user_id: string; occurred_at: number; type: string }> = [];
  for (const row of miniSessions) {
    const userId = String(row.user_id ?? "");
    const occurredAt = numberValue(row.last_used_at);
    if (userId && occurredAt) events.push({ user_id: userId, occurred_at: occurredAt, type: "mini_session_last_used" });
  }
  for (const row of miniUsers) {
    const userId = String(row.id ?? "");
    const occurredAt = numberValue(row.last_seen_at) || numberValue(row.updated_at);
    if (userId && occurredAt) events.push({ user_id: userId, occurred_at: occurredAt, type: "mini_user_last_seen" });
  }
  for (const row of snapshots) {
    const userId = String(row.user_id ?? "");
    const occurredAt = numberValue(row.updated_at);
    if (userId && occurredAt) events.push({ user_id: userId, occurred_at: occurredAt, type: "mini_snapshot_sync" });
  }
  return events;
}

function buildUserRows(
  authUsers: Array<any>,
  profiles: Json[],
  miniUsers: Json[],
  miniSessions: Json[],
  snapshots: Json[],
  appActivityEvents: Json[],
  liveTransactions: Json[],
  appFeedbackRows: Json[],
  miniFeedbackRows: Json[],
  selectedStart: number,
  selectedDate: string,
) {
  const profilesById = mapBy(profiles, "id");
  const selectedEnd = selectedStart + dayMs;
  const appActivity = buildAppActivityEvents(authUsers, profiles, appActivityEvents);
  const miniActivity = buildMiniActivityEvents(miniUsers, miniSessions, snapshots);
  const appActivityStats = summarizeActivity(appActivity, selectedStart, selectedEnd);
  const miniActivityStats = summarizeActivity(miniActivity, selectedStart, selectedEnd);
  const appTxStats = summarizeAppTransactions(liveTransactions, selectedStart, selectedEnd);
  const appFeedbackStats = summarizeFeedback(appFeedbackRows, "user_id");
  const miniTxStats = summarizeMiniTransactions(snapshots, selectedStart, selectedDate);
  const miniFeedbackStats = summarizeFeedback(miniFeedbackRows, "mini_user_id");
  const snapshotProfilesById = miniSnapshotProfiles(snapshots);
  const appActivityByUser = groupActivityByUser(appActivity);
  const miniActivityByUser = groupActivityByUser(miniActivity);
  const appRecordsByUser = groupRecordEventsByUser(liveTransactions);
  const miniRecordsByUser = groupMiniRecordEventsByUser(snapshots);

  const appRows = authUsers.map((user) => {
    const profile = profilesById.get(user.id) ?? {};
    const metadata = user.user_metadata ?? {};
    const tx = appTxStats.get(user.id) ?? emptyUserStats();
    const feedback = appFeedbackStats.get(user.id) ?? emptyFeedbackStats();
    const activity = appActivityStats.get(user.id) ?? emptyActivityStats();
    const registeredAt = numberValue(profile.created_at) || dateMs(user.created_at);
    const lastLoginAt = dateMs(user.last_sign_in_at);
    const activityRows = appActivityByUser.get(user.id) ?? [];
    const recordRows = appRecordsByUser.get(user.id) ?? [];
    return {
      source: "App",
      source_key: "app",
      user_id: user.id,
      display_name: stringValue(profile.display_name, 80) || stringValue(metadata.display_name, 80) || stringValue(user.email, 120) || shortId(user.id),
      email: stringValue(user.email, 160) || stringValue(profile.email, 160),
      contact: stringValue(profile.phone, 80),
      device_brand: stringValue(profile.device_brand, 80),
      device_model: stringValue(profile.device_model, 120),
      device_platform: stringValue(profile.device_platform, 32) || "android",
      app_version: stringValue(profile.app_version, 32),
      gender: genderValue(profile.gender) || genderValue(metadata.gender),
      birth_date: stringValue(profile.birth_date, 20) || stringValue(metadata.birth_date, 20),
      city: stringValue(profile.city, 80) || stringValue(metadata.city, 80),
      registered_at: registeredAt,
      last_login_at: lastLoginAt,
      last_seen_at: activity.last_activity_at,
      today_logged_in: activity.day_active,
      total_records: tx.total_records,
      today_records: tx.today_records,
      today_sync_records: tx.today_sync_records,
      last_record_at: tx.last_record_at,
      last_sync_at: tx.last_sync_at,
      feedback_count: feedback.feedback_count,
      last_feedback_at: feedback.last_feedback_at,
      role: stringValue(profile.role, 32) || "user",
      membership_tier: stringValue(profile.membership_tier, 32) || "free",
      activity_week: seriesByDay(activityRows, selectedStart, 7),
      activity_month: seriesByDay(activityRows, selectedStart, 30),
      activity_year: seriesByMonth(activityRows, selectedStart, 12),
      record_week: seriesByDay(recordRows, selectedStart, 7),
      record_month: seriesByDay(recordRows, selectedStart, 30),
      record_year: seriesByMonth(recordRows, selectedStart, 12),
      evidence: "App 活跃来自 app_activity_events / Auth 最后登录 / profiles 设备同步；旧版本没有完整打开流水",
    };
  });

  const miniRows = miniUsers.map((user: Json) => {
    const id = String(user.id);
    const activity = miniActivityStats.get(id) ?? emptyActivityStats();
    const tx = miniTxStats.get(id) ?? emptyUserStats();
    const feedback = miniFeedbackStats.get(id) ?? emptyFeedbackStats();
    const snapshotProfile = snapshotProfilesById.get(id) ?? {};
    const activityRows = miniActivityByUser.get(id) ?? [];
    const recordRows = miniRecordsByUser.get(id) ?? [];
    return {
      source: "小程序",
      source_key: "mini",
      user_id: id,
      display_name: stringValue(user.nickname, 80) || `小程序用户 ${shortId(id)}`,
      email: "",
      contact: "",
      device_brand: stringValue(user.device_brand, 80),
      device_model: stringValue(user.device_model, 120),
      device_platform: stringValue(user.device_platform, 32) || "wechat-mini",
      app_version: stringValue(user.app_version, 32),
      gender: genderValue(user.gender) || genderValue(snapshotProfile.gender),
      birth_date: stringValue(user.birth_date, 20) || stringValue(snapshotProfile.birth_date, 20),
      city: stringValue(user.city, 80) || stringValue(snapshotProfile.city, 80),
      registered_at: numberValue(user.created_at),
      last_login_at: activity.last_activity_at,
      last_seen_at: activity.last_activity_at,
      today_logged_in: activity.day_active,
      total_records: tx.total_records,
      today_records: tx.today_records,
      today_sync_records: tx.today_sync_records,
      last_record_at: tx.last_record_at,
      last_sync_at: tx.last_sync_at,
      feedback_count: feedback.feedback_count,
      last_feedback_at: feedback.last_feedback_at,
      role: "mini_user",
      membership_tier: "",
      activity_week: seriesByDay(activityRows, selectedStart, 7),
      activity_month: seriesByDay(activityRows, selectedStart, 30),
      activity_year: seriesByMonth(activityRows, selectedStart, 12),
      record_week: seriesByDay(recordRows, selectedStart, 7),
      record_month: seriesByDay(recordRows, selectedStart, 30),
      record_year: seriesByMonth(recordRows, selectedStart, 12),
      evidence: "小程序活跃来自 mini_sessions / mini_users.last_seen_at / 账本快照同步",
    };
  });

  return [...appRows, ...miniRows].sort((a, b) => {
    const byRegistered = numberValue(b.registered_at) - numberValue(a.registered_at);
    if (byRegistered !== 0) return byRegistered;
    return String(a.user_id).localeCompare(String(b.user_id));
  });
}

function miniSnapshotProfiles(snapshots: Json[]) {
  const rows = new Map<string, Json>();
  for (const snapshot of snapshots) {
    const userId = String(snapshot.user_id ?? "");
    if (!userId) continue;
    const payload = snapshot.payload && typeof snapshot.payload === "object" ? snapshot.payload as Json : {};
    const profile = payload.profile && typeof payload.profile === "object" ? payload.profile as Json : {};
    rows.set(userId, {
      gender: profile.gender,
      birth_date: profile.birthDate,
      city: profile.city,
    });
  }
  return rows;
}

function summarizeActivity(rows: Array<{ user_id: string; occurred_at: number }>, dayStart: number, dayEnd: number) {
  const stats = new Map<string, ReturnType<typeof emptyActivityStats>>();
  for (const row of rows) {
    const id = String(row.user_id ?? "");
    if (!id) continue;
    const stat = stats.get(id) ?? emptyActivityStats();
    stat.total_activity_events += 1;
    stat.last_activity_at = Math.max(stat.last_activity_at, numberValue(row.occurred_at));
    stat.day_active = stat.day_active || inRange(row.occurred_at, dayStart, dayEnd);
    stats.set(id, stat);
  }
  return stats;
}

function summarizeAppTransactions(rows: Json[], dayStart: number, dayEnd: number) {
  const stats = new Map<string, ReturnType<typeof emptyUserStats>>();
  for (const row of rows) {
    const id = String(row.user_id ?? "");
    if (!id) continue;
    const stat = stats.get(id) ?? emptyUserStats();
    stat.total_records += 1;
    if (inRange(numberValue(row.occurred_at), dayStart, dayEnd)) stat.today_records += 1;
    if (inRange(numberValue(row.updated_at), dayStart, dayEnd)) stat.today_sync_records += 1;
    stat.last_record_at = Math.max(stat.last_record_at, numberValue(row.occurred_at));
    stat.last_sync_at = Math.max(stat.last_sync_at, numberValue(row.updated_at));
    stats.set(id, stat);
  }
  return stats;
}

function summarizeMiniTransactions(snapshots: Json[], todayStart: number, todayKey: string) {
  const todayEnd = todayStart + dayMs;
  const stats = new Map<string, ReturnType<typeof emptyUserStats>>();
  for (const row of miniTransactionsFromSnapshots(snapshots)) {
    const id = row.user_id;
    if (!id) continue;
    const stat = stats.get(id) ?? emptyUserStats();
    stat.total_records += 1;
    if (row.date === todayKey) stat.today_records += 1;
    if (inRange(row.updated_at, todayStart, todayEnd)) stat.today_sync_records += 1;
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

function emptyActivityStats() {
  return { total_activity_events: 0, last_activity_at: 0, day_active: false };
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

function groupActivityByUser(rows: Array<{ user_id: string; occurred_at: number }>) {
  const grouped = new Map<string, Array<{ occurred_at: number }>>();
  for (const row of rows) {
    if (!row.user_id || !row.occurred_at) continue;
    const list = grouped.get(row.user_id) ?? [];
    list.push({ occurred_at: row.occurred_at });
    grouped.set(row.user_id, list);
  }
  return grouped;
}

function groupRecordEventsByUser(rows: Json[]) {
  const grouped = new Map<string, Array<{ occurred_at: number }>>();
  for (const row of rows) {
    const userId = String(row.user_id ?? "");
    const occurredAt = numberValue(row.occurred_at);
    if (!userId || !occurredAt) continue;
    const list = grouped.get(userId) ?? [];
    list.push({ occurred_at: occurredAt });
    grouped.set(userId, list);
  }
  return grouped;
}

function groupMiniRecordEventsByUser(snapshots: Json[]) {
  const grouped = new Map<string, Array<{ occurred_at: number }>>();
  for (const row of miniTransactionsFromSnapshots(snapshots)) {
    const occurredAt = row.record_at;
    if (!row.user_id || !occurredAt) continue;
    const list = grouped.get(row.user_id) ?? [];
    list.push({ occurred_at: occurredAt });
    grouped.set(row.user_id, list);
  }
  return grouped;
}

function seriesByDay(rows: Array<{ occurred_at: number }>, endDayStart: number, days: number) {
  const start = endDayStart - (days - 1) * dayMs;
  return Array.from({ length: days }, (_, index) => {
    const day = start + index * dayMs;
    const end = day + dayMs;
    return {
      date: monthDayKeyChina(day),
      value: rows.filter((row) => inRange(row.occurred_at, day, end)).length,
    };
  });
}

function seriesByMonth(rows: Array<{ occurred_at: number }>, endDayStart: number, months: number) {
  const endDate = new Date(endDayStart + chinaOffsetMs);
  const endYear = endDate.getUTCFullYear();
  const endMonth = endDate.getUTCMonth();
  return Array.from({ length: months }, (_, index) => {
    const cursor = new Date(Date.UTC(endYear, endMonth - (months - 1 - index), 1));
    const next = new Date(Date.UTC(cursor.getUTCFullYear(), cursor.getUTCMonth() + 1, 1));
    const start = cursor.getTime() - chinaOffsetMs;
    const end = next.getTime() - chinaOffsetMs;
    return {
      date: cursor.toISOString().slice(2, 7),
      value: rows.filter((row) => inRange(row.occurred_at, start, end)).length,
    };
  });
}

function inRange(value: number, start: number, end: number): boolean {
  return value >= start && value < end;
}

function startOfChinaDay(value: number): number {
  return Math.floor((value + chinaOffsetMs) / dayMs) * dayMs - chinaOffsetMs;
}

function requestedDateKey(value: unknown, fallback: number): string {
  const raw = stringValue(value, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : dateKeyChina(fallback);
}

function dayStartFromKey(value: string): number {
  const parsed = Date.parse(`${value}T00:00:00+08:00`);
  return Number.isFinite(parsed) ? parsed : startOfChinaDay(Date.now());
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

function genderValue(value: unknown): string {
  const raw = stringValue(value, 24);
  return ["female", "male", "other", "prefer_not"].includes(raw) ? raw : "";
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
