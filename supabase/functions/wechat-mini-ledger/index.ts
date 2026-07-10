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
const chinaOffsetMs = 8 * 60 * 60 * 1000;
const nicknameWindowMs = 180 * 24 * 60 * 60 * 1000;
let wechatAccessTokenCache: { token: string; expiresAt: number } | null = null;

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
    if (action === "feedback.replies") return await listFeedbackReplies(req);
    if (action === "email.requestCode") return await requestEmailCode(req, body);
    if (action === "email.verifyCode") return await verifyEmailCode(req, body);
    if (action === "phone.bind") return await bindPhone(req, body);
    if (action === "avatar.check") return await checkAvatarImage(body);
    if (action === "birthday.sendDue") return await sendDueBirthdayGreetings(req, body);
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
  const clientInfo = clientInfoValue(body.client);
  const user = await rest("POST", "/rest/v1/mini_users?on_conflict=appid,openid&select=*", [{
    appid: wechatAppId,
    openid: session.openid,
    unionid: session.unionid ?? null,
    ...clientPatch(clientInfo),
    last_seen_at: now,
    updated_at: now,
  }], {
    Prefer: "resolution=merge-duplicates,return=representation",
  }).then((rows) => rows[0]);
  const accountNo = accountNoValue(user.account_no) || await assignMiniAccountNo(user.id);

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

  const snapshot = await loadSnapshot(String(user.id));
  return json({
    token,
    userId: user.id,
    accountNo,
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
  await updateMiniUserProfile(session.user_id, payload, now, clientInfoValue(body.client));
  return json({ ok: true, updatedAt: now });
}

async function pullSnapshot(req: Request): Promise<Response> {
  const session = await requireMiniSession(req);
  const snapshot = await loadSnapshot(String(session.user_id));
  return json({ ok: true, ledger: snapshot?.payload ?? null, updatedAt: snapshot?.updated_at ?? null });
}

async function listMessages(): Promise<Response> {
  const rows = await rest(
    "GET",
    "/rest/v1/official_messages?select=id,title,body,source_key,created_at,updated_at&order=created_at.desc&limit=50",
  );
  return json({ ok: true, messages: rows.filter(isMiniVisibleMessage) });
}

function isMiniVisibleMessage(row: Json): boolean {
  const source = String(row.source_key ?? "").toLowerCase();
  if (!source) return true;
  if (source.includes("mini") || source.includes("wechat") || source.includes("小程序") || /(^|[:_-])mp($|[:_-])/.test(source)) return true;
  if (source.includes("android") || /(^|[:_-])app($|[:_-])/.test(source) || source.startsWith("release:")) return false;
  return true;
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

async function listFeedbackReplies(req: Request): Promise<Response> {
  const session = await requireMiniSession(req);
  const rows = await rest(
    "GET",
    `/rest/v1/mini_feedback?select=id,content,category,status,developer_reply,replied_at,reply_seen_at,created_at,updated_at&mini_user_id=eq.${encodeURIComponent(String(session.user_id))}&developer_reply=not.is.null&order=replied_at.desc&limit=50`,
  );
  const now = Date.now();
  const unseenIds = rows
    .filter((row) => !row.reply_seen_at)
    .map((row) => String(row.id))
    .filter(Boolean);
  if (unseenIds.length) {
    await rest(
      "PATCH",
      `/rest/v1/mini_feedback?id=in.(${unseenIds.join(",")})`,
      { reply_seen_at: now, updated_at: now },
      { Prefer: "return=minimal" },
    );
  }
  return json({ ok: true, replies: rows });
}

async function requestEmailCode(req: Request, body: Json): Promise<Response> {
  const session = await requireMiniSession(req);
  const email = emailValue(body.email);
  if (!email) return json({ error: "请输入正确邮箱" }, 400);
  const now = Date.now();
  const code = sixDigitCode();
  const codeHash = await emailCodeHash(email, code);
  await rest("POST", "/rest/v1/mini_email_verifications", [{
    user_id: session.user_id,
    email,
    code_hash: codeHash,
    expires_at: now + 10 * 60 * 1000,
    created_at: now,
  }], { Prefer: "return=minimal" });
  await sendEmail(
    email,
    "绒绒记账邮箱验证码",
    `<p>你的绒绒记账验证码是：</p><p style="font-size:28px;font-weight:700;letter-spacing:4px;">${code}</p><p>10 分钟内有效。如果不是你本人操作，可以忽略这封邮件。</p>`,
  );
  return json({ ok: true, expiresAt: now + 10 * 60 * 1000 });
}

async function verifyEmailCode(req: Request, body: Json): Promise<Response> {
  const session = await requireMiniSession(req);
  const email = emailValue(body.email);
  const code = String(body.code ?? "").replace(/\D/g, "").slice(0, 6);
  if (!email || code.length !== 6) return json({ error: "验证码不正确" }, 400);
  const rows = await rest(
    "GET",
    `/rest/v1/mini_email_verifications?select=id,code_hash,expires_at,consumed_at&user_id=eq.${encodeURIComponent(String(session.user_id))}&email=eq.${encodeURIComponent(email)}&consumed_at=is.null&order=created_at.desc&limit=5`,
  );
  const codeHash = await emailCodeHash(email, code);
  const now = Date.now();
  const matched = rows.find((row) => row.code_hash === codeHash && numberValue(row.expires_at) >= now);
  if (!matched) return json({ error: "验证码已过期或不正确" }, 400);
  await rest(
    "PATCH",
    `/rest/v1/mini_email_verifications?id=eq.${encodeURIComponent(String(matched.id))}`,
    { consumed_at: now },
    { Prefer: "return=minimal" },
  );
  await rest(
    "PATCH",
    `/rest/v1/mini_users?id=eq.${encodeURIComponent(String(session.user_id))}`,
    { email, email_verified_at: now, updated_at: now },
    { Prefer: "return=minimal" },
  );
  return json({ ok: true, email, verifiedAt: now });
}

async function sendDueBirthdayGreetings(req: Request, body: Json): Promise<Response> {
  const secret = Deno.env.get("BIRTHDAY_CRON_SECRET") ?? "";
  const requestSecret = req.headers.get("x-cron-secret") ?? stringValue(body.secret, 120);
  if (!secret || requestSecret !== secret) return json({ error: "无权触发生日祝福任务" }, 403);
  const today = requestedDateKey(body.date, Date.now());
  const year = Number(today.slice(0, 4));
  const monthDay = today.slice(5);
  const rows = await rest(
    "GET",
    "/rest/v1/mini_users?select=id,openid,nickname,email,email_verified_at,province,city,district,birth_date,birthday_wechat_enabled,birthday_email_enabled,last_birthday_wechat_year,last_birthday_email_year&birth_date=not.is.null&limit=1000",
  );
  const due = rows.filter((row) => String(row.birth_date || "").slice(5) === monthDay);
  let emailSent = 0;
  let wechatSent = 0;
  const errors: string[] = [];
  for (const user of due) {
    const id = String(user.id || "");
    if (!id) continue;
    const nickname = stringValue(user.nickname, 40) || "绒绒用户";
    const place = stringValue(user.city, 80) || stringValue(user.province, 80);
    if (user.birthday_email_enabled && user.email && user.email_verified_at && Number(user.last_birthday_email_year) !== year) {
      try {
        await sendEmail(
          String(user.email),
          "绒绒记账生日祝福",
          `<p>${nickname}，生日快乐！</p><p>${birthdayGreeting(place)}</p><p>愿今天的每一笔开心，都值得被认真记下。</p>`,
        );
        await updateBirthdaySent(id, "email", year);
        emailSent += 1;
      } catch (error) {
        errors.push(`email:${id}:${friendlyError(error)}`);
      }
    }
    if (user.birthday_wechat_enabled && user.openid && Number(user.last_birthday_wechat_year) !== year) {
      try {
        const ok = await sendWechatBirthdayMessage(String(user.openid), nickname, place, today);
        if (ok) {
          await updateBirthdaySent(id, "wechat", year);
          wechatSent += 1;
        }
      } catch (error) {
        errors.push(`wechat:${id}:${friendlyError(error)}`);
      }
    }
  }
  return json({ ok: true, date: today, due: due.length, emailSent, wechatSent, errors: errors.slice(0, 20) });
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

async function checkAvatarImage(body: Json): Promise<Response> {
  assertConfigured();
  const imageBase64 = stringValue(body.imageBase64, 2_000_000).replace(/^data:image\/\w+;base64,/, "");
  const size = numberValue(body.size);
  const mimeType = avatarMimeType(body.mimeType);
  if (!imageBase64) return json({ error: "缺少头像图片" }, 400);
  if (size > 1024 * 1024 || imageBase64.length > 1_500_000) return json({ error: "头像图片过大，请换一张较小图片" }, 413);

  const bytes = base64ToBytes(imageBase64);
  if (!bytes.length) return json({ error: "头像图片读取失败" }, 400);
  const token = await wechatAccessToken();
  const formData = new FormData();
  const media = new ArrayBuffer(bytes.byteLength);
  new Uint8Array(media).set(bytes);
  formData.append("media", new Blob([media], { type: mimeType }), `avatar.${mimeType.split("/")[1] || "jpg"}`);
  const response = await fetch(`https://api.weixin.qq.com/wxa/img_sec_check?access_token=${encodeURIComponent(token)}`, {
    method: "POST",
    body: formData,
  });
  const result = await response.json().catch(() => ({}));
  const errcode = Number(result.errcode ?? 0);
  if (response.ok && errcode === 0) return json({ ok: true, safe: true, api: "img_sec_check" });
  if (errcode === 87014) return json({ ok: false, safe: false, error: "头像含违规信息，请更换后再试" }, 400);
  throw new Error(`微信头像安全检测失败：${result.errmsg ?? response.status}`);
}

async function bindPhone(req: Request, body: Json): Promise<Response> {
  assertConfigured();
  const session = await requireMiniSession(req);
  const code = stringValue(body.code, 256);
  if (!code) return json({ error: "缺少手机号授权 code" }, 400);

  const token = await wechatAccessToken();
  const response = await fetch(`https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=${encodeURIComponent(token)}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ code }),
  });
  const result = await response.json().catch(() => ({}));
  const errcode = Number(result.errcode ?? 0);
  if (!response.ok || errcode !== 0) {
    throw new Error(`微信手机号授权失败：${result.errmsg ?? response.status}`);
  }

  const phoneInfo = result.phone_info && typeof result.phone_info === "object" ? result.phone_info as Json : {};
  const phone = phoneValue(phoneInfo.phoneNumber ?? phoneInfo.purePhoneNumber);
  if (!phone) return json({ error: "微信未返回有效手机号" }, 502);
  let persisted = true;
  try {
    await rest(
      "PATCH",
      `/rest/v1/mini_users?id=eq.${encodeURIComponent(String(session.user_id))}`,
      { phone, updated_at: Date.now() },
      { Prefer: "return=minimal" },
    );
  } catch {
    persisted = false;
  }
  return json({ ok: true, phone, persisted });
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

async function updateMiniUserProfile(userId: unknown, payload: unknown, now: number, clientInfo: Json): Promise<void> {
  const profile = profileFromPayload(payload);
  const current = await loadMiniUser(userId);
  const accountNo = accountNoValue(profile.accountNo);
  const nickname = profileNickname(profile);
  const gender = genderValue(profile.gender);
  const birthDate = birthDateValue(profile.birthDate);
  const province = stringValue(profile.province, 80);
  const city = stringValue(profile.city, 80);
  const district = stringValue(profile.district, 80);
  const signature = stringValue(profile.signature, 120);
  const patch: Json = {
    ...clientPatch(clientInfo),
    updated_at: now,
    last_seen_at: now,
  };
  if (accountNo) applyAccountNoPatch(patch, current, accountNo, now);
  const nicknameHistory = nickname
    ? await applyNicknamePatch(patch, current, nickname, userId, profile.nicknameHistory, now)
    : profile.nicknameHistory;
  if (gender) patch.gender = gender;
  if (birthDate) patch.birth_date = birthDate;
  if (province) patch.province = province;
  if (city) patch.city = city;
  if (district) patch.district = district;
  if (signature) patch.signature = signature;
  patch.birthday_wechat_enabled = Boolean(profile.birthdayWechatSubscribeEnabled);
  patch.birthday_email_enabled = Boolean(profile.birthdayEmailEnabled);
  try {
    await rest(
      "PATCH",
      `/rest/v1/mini_users?id=eq.${encodeURIComponent(String(userId))}`,
      patch,
      { Prefer: "return=minimal" },
    );
  } catch (error) {
    if (isAccountNoUniqueError(error)) throw new Error("用户ID已被占用，请换一个");
    throw error;
  }
  await syncMiniNameHistory(userId, nicknameHistory);
}

async function loadMiniUser(userId: unknown): Promise<Json> {
  const rows = await rest(
    "GET",
    `/rest/v1/mini_users?select=id,account_no,account_no_changed_year,account_no_changed_count,nickname&id=eq.${encodeURIComponent(String(userId))}&limit=1`,
  );
  return rows[0] ?? {};
}

async function assignMiniAccountNo(userId: unknown): Promise<string> {
  for (let index = 0; index < 8; index += 1) {
    const accountNo = randomAccountNo();
    try {
      await rest(
        "PATCH",
        `/rest/v1/mini_users?id=eq.${encodeURIComponent(String(userId))}`,
        { account_no: accountNo, updated_at: Date.now() },
        { Prefer: "return=minimal" },
      );
      return accountNo;
    } catch (error) {
      const message = String(error instanceof Error ? error.message : error).toLowerCase();
      if (!message.includes("duplicate") && !message.includes("unique") && !message.includes("23505")) throw error;
    }
  }
  throw new Error("用户ID生成失败，请稍后再试");
}

function applyAccountNoPatch(patch: Json, current: Json, accountNo: string, now: number): void {
  const currentAccountNo = accountNoValue(current.account_no);
  if (!currentAccountNo) {
    patch.account_no = accountNo;
    return;
  }
  if (currentAccountNo.toLowerCase() === accountNo.toLowerCase()) return;
  const year = new Date(now + chinaOffsetMs).getUTCFullYear().toString();
  const used = String(current.account_no_changed_year || "") === year ? numberValue(current.account_no_changed_count) : 0;
  if (used >= 2) throw new Error("用户ID今年已修改2次，云端未保存本次修改");
  patch.account_no = accountNo;
  patch.account_no_changed_year = year;
  patch.account_no_changed_count = used + 1;
}

async function applyNicknamePatch(
  patch: Json,
  current: Json,
  nickname: string,
  userId: unknown,
  historyValue: unknown,
  now: number,
): Promise<unknown> {
  const currentNickname = stringValue(current.nickname, 80);
  if (!currentNickname) {
    patch.nickname = nickname;
    return historyValue;
  }
  if (currentNickname === nickname) return historyValue;
  const since = now - nicknameWindowMs;
  const recentRows = await rest(
    "GET",
    `/rest/v1/mini_profile_name_history?select=id&user_id=eq.${encodeURIComponent(String(userId))}&changed_at=gte.${since}&limit=4`,
  );
  if (recentRows.length >= 3) throw new Error("昵称180天内已修改3次，云端未保存本次修改");
  patch.nickname = nickname;
  const rows = Array.isArray(historyValue) ? historyValue : [];
  const alreadyRecorded = rows.some((item) => {
    const row = item && typeof item === "object" ? item as Json : {};
    const oldName = stringValue(row.oldNickname ?? row.old_display_name, 80);
    const newName = stringValue(row.newNickname ?? row.new_display_name, 80);
    return oldName === currentNickname && newName === nickname;
  });
  if (alreadyRecorded) return historyValue;
  return [
    {
      id: `name-server-${now}-${randomToken().slice(0, 8)}`,
      oldNickname: currentNickname,
      newNickname: nickname,
      changedAt: now,
      source: "mini",
    },
    ...rows,
  ];
}

async function syncMiniNameHistory(userId: unknown, value: unknown): Promise<void> {
  if (!Array.isArray(value) || value.length === 0) return;
  const rows = value
    .map((item) => item && typeof item === "object" ? item as Json : {})
    .map((item) => ({
      id: stringValue(item.id, 80),
      user_id: userId,
      old_display_name: stringValue(item.oldNickname ?? item.old_display_name, 80),
      new_display_name: stringValue(item.newNickname ?? item.new_display_name, 80),
      changed_at: numberValue(item.changedAt ?? item.changed_at),
      source: "mini",
    }))
    .filter((item) => item.id && item.changed_at && item.old_display_name !== item.new_display_name)
    .slice(0, 50);
  if (!rows.length) return;
  await rest(
    "POST",
    "/rest/v1/mini_profile_name_history?on_conflict=id",
    rows,
    { Prefer: "resolution=ignore-duplicates,return=minimal" },
  );
}

function profileFromPayload(payload: unknown): Json {
  const ledger = payload && typeof payload === "object" ? payload as Json : {};
  return ledger.profile && typeof ledger.profile === "object" ? ledger.profile as Json : {};
}

function profileNickname(profile: Json): string {
  const nickname = stringValue(profile.nickname, 80);
  return nickname && nickname !== "绒绒用户" ? nickname : "";
}

function accountNoValue(value: unknown): string {
  const raw = stringValue(value, 6).toUpperCase();
  return /^[A-Za-z0-9_]{6}$/.test(raw) ? raw : "";
}

function isAccountNoUniqueError(error: unknown): boolean {
  const text = String(error instanceof Error ? error.message : error).toLowerCase();
  return text.includes("mini_users_account_no_unique_idx") || text.includes("duplicate") || text.includes("23505");
}

function randomAccountNo(): string {
  const bytes = new Uint8Array(6);
  crypto.getRandomValues(bytes);
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let result = "";
  for (let index = 0; index < 6; index += 1) {
    result += chars[bytes[index] % chars.length];
  }
  return result;
}

function clientInfoValue(value: unknown): Json {
  return value && typeof value === "object" ? value as Json : {};
}

function clientPatch(clientInfo: Json): Json {
  return {
    device_brand: stringValue(clientInfo.brand, 80) || null,
    device_model: stringValue(clientInfo.model, 120) || null,
    device_platform: stringValue(clientInfo.platform, 32) || null,
    app_version: stringValue(clientInfo.version, 32) || null,
  };
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

function genderValue(value: unknown): string {
  const raw = stringValue(value, 24);
  return ["female", "male", "other", "prefer_not"].includes(raw) ? raw : "";
}

function birthDateValue(value: unknown): string {
  const raw = stringValue(value, 20);
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : "";
}

function emailValue(value: unknown): string {
  const raw = stringValue(value, 160).toLowerCase();
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(raw) ? raw : "";
}

function phoneValue(value: unknown): string {
  const raw = stringValue(value, 32).replace(/[^\d+]/g, "");
  if (/^\+?\d{6,20}$/.test(raw)) return raw;
  return "";
}

function avatarMimeType(value: unknown): string {
  const raw = stringValue(value, 40).toLowerCase();
  if (raw === "image/png") return raw;
  if (raw === "image/webp") return raw;
  return "image/jpeg";
}

function numberValue(value: unknown): number {
  const number = Number(value);
  return Number.isFinite(number) ? number : 0;
}

function base64ToBytes(value: string): Uint8Array {
  try {
    const binary = atob(value);
    const bytes = new Uint8Array(binary.length);
    for (let index = 0; index < binary.length; index += 1) bytes[index] = binary.charCodeAt(index);
    return bytes;
  } catch {
    return new Uint8Array();
  }
}

function requestedDateKey(value: unknown, fallback: number): string {
  const raw = stringValue(value, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(raw) ? raw : new Date(fallback + chinaOffsetMs).toISOString().slice(0, 10);
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

function sixDigitCode(): string {
  const bytes = new Uint32Array(1);
  crypto.getRandomValues(bytes);
  return String(100000 + (bytes[0] % 900000));
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest)).map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function emailCodeHash(email: string, code: string): Promise<string> {
  const secret = Deno.env.get("EMAIL_CODE_SECRET") || serviceRoleKey;
  if (!secret) throw new Error("EMAIL_CODE_SECRET 未配置");
  return await sha256(`${email}:${code}:${secret}`);
}

async function sendEmail(to: string, subject: string, html: string): Promise<void> {
  const apiKey = Deno.env.get("RESEND_API_KEY") ?? "";
  const from = Deno.env.get("BIRTHDAY_EMAIL_FROM") ?? Deno.env.get("EMAIL_FROM") ?? "";
  if (!apiKey || !from) throw new Error("邮箱服务未配置");
  const response = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: {
      authorization: `Bearer ${apiKey}`,
      "content-type": "application/json",
    },
    body: JSON.stringify({ from, to, subject, html }),
  });
  const text = await response.text();
  if (!response.ok) throw new Error(`邮件发送失败 ${response.status}: ${text.slice(0, 120)}`);
}

function birthdayGreeting(place: string): string {
  if (place) return `来自 ${place} 的专属祝福已经送达。`;
  return "今天给你留一张专属生日祝福卡。";
}

async function updateBirthdaySent(userId: string, channel: "email" | "wechat", year: number): Promise<void> {
  const field = channel === "email" ? "last_birthday_email_year" : "last_birthday_wechat_year";
  await rest(
    "PATCH",
    `/rest/v1/mini_users?id=eq.${encodeURIComponent(userId)}`,
    { [field]: year, updated_at: Date.now() },
    { Prefer: "return=minimal" },
  );
}

async function sendWechatBirthdayMessage(openid: string, nickname: string, place: string, today: string): Promise<boolean> {
  const templateId = Deno.env.get("BIRTHDAY_WECHAT_TEMPLATE_ID") ?? "";
  if (!templateId) throw new Error("微信生日祝福模板未配置");
  const token = await wechatAccessToken();
  const data = wechatTemplateData(nickname, place, today);
  const response = await fetch(`https://api.weixin.qq.com/cgi-bin/message/subscribe/send?access_token=${encodeURIComponent(token)}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({
      touser: openid,
      template_id: templateId,
      page: "pages/my/my",
      data,
    }),
  });
  const result = await response.json();
  if (!response.ok || result.errcode) throw new Error(result.errmsg || `微信订阅消息失败 ${response.status}`);
  return true;
}

async function wechatAccessToken(): Promise<string> {
  assertConfigured();
  const now = Date.now();
  if (wechatAccessTokenCache && wechatAccessTokenCache.expiresAt > now + 60_000) return wechatAccessTokenCache.token;
  const url = new URL("https://api.weixin.qq.com/cgi-bin/token");
  url.searchParams.set("grant_type", "client_credential");
  url.searchParams.set("appid", wechatAppId);
  url.searchParams.set("secret", wechatSecret);
  const response = await fetch(url);
  const data = await response.json();
  if (!response.ok || data.errcode || !data.access_token) {
    throw new Error(`微信 access_token 获取失败：${data.errmsg ?? response.status}`);
  }
  wechatAccessTokenCache = {
    token: String(data.access_token),
    expiresAt: now + Math.max(60, Number(data.expires_in || 7200) - 300) * 1000,
  };
  return wechatAccessTokenCache.token;
}

function wechatTemplateData(nickname: string, place: string, today: string): Json {
  const custom = Deno.env.get("BIRTHDAY_WECHAT_TEMPLATE_DATA_JSON");
  if (custom) {
    return JSON.parse(
      custom
        .replaceAll("{nickname}", nickname)
        .replaceAll("{place}", place || "绒绒星球")
        .replaceAll("{date}", today),
    ) as Json;
  }
  return {
    thing1: { value: "生日快乐" },
    thing2: { value: `${nickname}，${place ? `${place}专属` : "你的"}生日祝福已送达` },
    date3: { value: today },
  };
}

function friendlyError(error: unknown): string {
  return error instanceof Error ? error.message : "服务暂时不可用";
}
