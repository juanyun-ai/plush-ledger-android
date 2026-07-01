import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type Json = Record<string, unknown>;

const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "") ?? "";
const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const defaultWechatMiniAppId = "wx3124ddc53c168286";
const wechatAppId = Deno.env.get("WECHAT_MINI_APPID") ?? defaultWechatMiniAppId;
const wechatSecret = Deno.env.get("WECHAT_MINI_SECRET") ?? Deno.env.get("密钥") ?? "";

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return json({ ok: true });
  if (req.method !== "POST") return json({ error: "只支持 POST 请求" }, 405);

  try {
    const body = await safeJson(req);
    const action = String(body.action ?? "");
    if (action === "login") return await login(body);
    if (action === "push") return await pushSnapshot(req, body);
    if (action === "pull") return await pullSnapshot(req);
    if (action === "messages.list") return await listMessages();
    if (action === "feedback.submit") return await submitFeedback(req, body);
    return json({ error: "未知操作" }, 400);
  } catch (error) {
    return json({ error: friendlyError(error) }, 500);
  }
});

async function login(body: Json): Promise<Response> {
  assertConfigured();
  const code = String(body.code ?? "").trim();
  if (!code) return json({ error: "缺少微信登录 code" }, 400);

  const session = await codeToSession(code);
  if (!session.openid) return json({ error: "微信登录失败：未返回 openid" }, 502);

  const now = Date.now();
  const user = await rest("POST", "/rest/v1/mini_users?on_conflict=appid,openid&select=*", [{
    appid: wechatAppId,
    openid: session.openid,
    unionid: session.unionid ?? null,
    updated_at: now,
  }], {
    Prefer: "resolution=merge-duplicates,return=representation",
  }).then((rows) => rows[0]);

  const token = randomToken();
  const tokenHash = await sha256(token);
  const expiresAt = now + 1000 * 60 * 60 * 24 * 30;
  await rest("POST", "/rest/v1/mini_sessions", [{
    user_id: user.id,
    token_hash: tokenHash,
    expires_at: expiresAt,
    created_at: now,
    last_used_at: now,
  }], { Prefer: "return=minimal" });

  const snapshot = await loadSnapshot(user.id);
  return json({
    token,
    userId: user.id,
    expiresAt,
    ledger: snapshot?.payload ?? null,
  });
}

async function pushSnapshot(req: Request, body: Json): Promise<Response> {
  const session = await requireMiniSession(req);
  const payload = body.payload;
  if (!payload || typeof payload !== "object") return json({ error: "缺少账本数据" }, 400);
  const raw = JSON.stringify(payload);
  if (raw.length > 512 * 1024) return json({ error: "账本数据过大，请先导出备份后再同步" }, 413);

  const now = Date.now();
  await rest("POST", "/rest/v1/mini_ledger_snapshots?on_conflict=user_id&select=updated_at", [{
    user_id: session.user_id,
    payload,
    updated_at: now,
  }], {
    Prefer: "resolution=merge-duplicates,return=representation",
  });
  return json({ ok: true, updatedAt: now });
}

async function pullSnapshot(req: Request): Promise<Response> {
  const session = await requireMiniSession(req);
  const snapshot = await loadSnapshot(session.user_id);
  return json({ ok: true, ledger: snapshot?.payload ?? null, updatedAt: snapshot?.updated_at ?? null });
}

async function listMessages(): Promise<Response> {
  const rows = await rest(
    "GET",
    "/rest/v1/official_messages?select=id,title,body,source_key,created_at,updated_at&order=created_at.desc&limit=50",
  );
  return json({ ok: true, messages: rows });
}

async function submitFeedback(req: Request, body: Json): Promise<Response> {
  assertConfigured();
  const content = stringValue(body.content, 500);
  const contact = stringValue(body.contact, 80) || null;
  const category = categoryValue(body.category);
  const page = stringValue(body.page, 120) || null;
  const appVersion = stringValue(body.appVersion, 32) || null;
  const clientInfo = body.client && typeof body.client === "object" ? body.client : {};
  if (content.length < 5) return json({ error: "反馈内容至少 5 个字" }, 400);

  const session = await optionalMiniSession(req);
  const now = Date.now();
  await rest("POST", "/rest/v1/mini_feedback", [{
    mini_user_id: session?.user_id ?? null,
    contact,
    content,
    category,
    source: "mini_program",
    page,
    app_version: appVersion,
    client_info: clientInfo,
    created_at: now,
    updated_at: now,
  }], { Prefer: "return=minimal" });
  return json({ ok: true, createdAt: now });
}

async function codeToSession(code: string): Promise<Json> {
  const url = new URL("https://api.weixin.qq.com/sns/jscode2session");
  url.searchParams.set("appid", wechatAppId);
  url.searchParams.set("secret", wechatSecret);
  url.searchParams.set("js_code", code);
  url.searchParams.set("grant_type", "authorization_code");
  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok || data.errcode) {
    throw new Error(`微信登录失败：${data.errmsg ?? response.status}`);
  }
  return data;
}

async function requireMiniSession(req: Request): Promise<Json> {
  assertConfigured();
  const auth = req.headers.get("authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token) throw new Error("请先微信登录");
  const tokenHash = await sha256(token);
  const rows = await rest(
    "GET",
    `/rest/v1/mini_sessions?select=user_id,expires_at&token_hash=eq.${encodeURIComponent(tokenHash)}&limit=1`,
  );
  const session = rows[0];
  if (!session) throw new Error("登录已失效，请重新登录");
  if (Number(session.expires_at) < Date.now()) throw new Error("登录已过期，请重新登录");
  await rest(
    "PATCH",
    `/rest/v1/mini_sessions?token_hash=eq.${encodeURIComponent(tokenHash)}`,
    { last_used_at: Date.now() },
    { Prefer: "return=minimal" },
  );
  return session;
}

async function optionalMiniSession(req: Request): Promise<Json | null> {
  const auth = req.headers.get("authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7).trim() : "";
  if (!token) return null;
  try {
    return await requireMiniSession(req);
  } catch {
    return null;
  }
}

async function loadSnapshot(userId: string): Promise<Json | null> {
  const rows = await rest(
    "GET",
    `/rest/v1/mini_ledger_snapshots?select=payload,updated_at&user_id=eq.${encodeURIComponent(userId)}&limit=1`,
  );
  return rows[0] ?? null;
}

async function rest(method: string, path: string, body?: unknown, extraHeaders: Record<string, string> = {}): Promise<Json[]> {
  if (!supabaseUrl || !serviceRoleKey) throw new Error("Supabase 后端环境变量未配置");
  const response = await fetch(`${supabaseUrl}${path}`, {
    method,
    headers: {
      apikey: serviceRoleKey,
      authorization: `Bearer ${serviceRoleKey}`,
      "content-type": "application/json",
      ...extraHeaders,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`Supabase 请求失败 ${response.status}: ${text.slice(0, 160)}`);
  if (!text) return [];
  const data = JSON.parse(text);
  return Array.isArray(data) ? data : [data];
}

function assertConfigured() {
  if (!wechatAppId) {
    throw new Error("微信小程序 AppID 未配置");
  }
  if (!wechatSecret) {
    throw new Error("微信小程序 AppSecret 未配置，请先在 Supabase Edge Function Secrets 中设置 WECHAT_MINI_SECRET");
  }
}

async function safeJson(req: Request): Promise<Json> {
  const text = await req.text();
  if (!text) return {};
  return JSON.parse(text);
}

function json(payload: unknown, status = 200): Response {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { ...corsHeaders, "content-type": "application/json; charset=utf-8" },
  });
}

function stringValue(value: unknown, maxLength: number): string {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function categoryValue(value: unknown): string {
  const raw = stringValue(value, 24);
  return ["bug", "suggestion", "data", "other"].includes(raw) ? raw : "suggestion";
}

function randomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function friendlyError(error: unknown): string {
  return error instanceof Error ? error.message : "服务暂时不可用";
}
