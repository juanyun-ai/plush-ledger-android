const STORAGE_KEY = "rongrong-admin-settings";
const SESSION_KEY = "rongrong-admin-session";

const DEFAULT_SETTINGS = {
  supabaseUrl: "https://tjcijqvweivqgqfpoehf.supabase.co",
  supabaseAnonKey: "sb_publishable_Lqzz2ClyU3eIlHvoJa7Gsw_1UBi1HT9",
};

const state = {
  settings: loadSettings(),
  session: loadJson(SESSION_KEY, null),
  dashboard: null,
  activeTab: "feedback",
};

const els = {
  setupPanel: byId("setupPanel"),
  loginPanel: byId("loginPanel"),
  appPanel: byId("appPanel"),
  setupForm: byId("setupForm"),
  loginForm: byId("loginForm"),
  sessionLabel: byId("sessionLabel"),
  refreshBtn: byId("refreshBtn"),
  logoutBtn: byId("logoutBtn"),
  configBtn: byId("configBtn"),
  toast: byId("toast"),
  feedbackRows: byId("feedbackRows"),
  feedbackSearch: byId("feedbackSearch"),
  messageForm: byId("messageForm"),
  versionForm: byId("versionForm"),
  configForm: byId("configForm"),
};

const links = [
  ["产品官网", "https://privacy.xiaoxing.online/", "用户看到的下载与隐私入口"],
  ["管理后台", "https://admin.xiaoxing.online/", "当前后台地址"],
  ["GitHub 仓库", "https://github.com/juanyun-ai/plush-ledger-android", "Android 源码、Pages 和 Releases"],
  ["GitHub Releases", "https://github.com/juanyun-ai/plush-ledger-android/releases", "上传和检查 APK 发布包"],
  ["Supabase", "https://supabase.com/dashboard/projects", "数据库、Auth、Storage、Edge Functions"],
  ["Cloudflare Pages", "https://dash.cloudflare.com/", "官网与管理台静态托管"],
  ["火山引擎 DNS", "https://console.volcengine.com/TrafficRoute/dns/publiczone/zones?", "xiaoxing.online 域名解析"],
  ["火山对象存储", "https://console.volcengine.com/tos", "TOS 资源包、桶和文件"],
  ["火山费用中心", "https://console.volcengine.com/finance/renew/", "资源包续费与到期管理"],
  ["微信公众平台", "https://mp.weixin.qq.com/", "小程序审核、版本和隐私协议"],
  ["DeepSeek 控制台", "https://platform.deepseek.com/", "AI Key、余额和调用情况"],
  ["产品隐私政策", "https://privacy.xiaoxing.online/privacy.html", "App 内跳转的隐私政策页面"],
];

init();

function init() {
  byId("supabaseUrl").value = state.settings.supabaseUrl || "";
  byId("supabaseAnonKey").value = state.settings.supabaseAnonKey || "";
  renderLinks();
  bindEvents();
  renderShell();
  if (isReady()) loadDashboard();
}

function bindEvents() {
  els.setupForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const supabaseUrl = normalizeSupabaseUrl(byId("supabaseUrl").value);
    const supabaseAnonKey = byId("supabaseAnonKey").value.trim();
    if (!supabaseUrl) return toast("请输入 https://xxxx.supabase.co 格式的项目 URL", true);
    if (!supabaseAnonKey) return toast("请输入 Supabase anon key", true);
    state.settings = {
      supabaseUrl,
      supabaseAnonKey,
    };
    saveJson(STORAGE_KEY, state.settings);
    toast("连接配置已保存");
    renderShell();
  });

  els.loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    await login(byId("email").value.trim(), byId("password").value);
  });

  els.refreshBtn.addEventListener("click", () => loadDashboard());
  els.logoutBtn.addEventListener("click", logout);
  els.configBtn.addEventListener("click", resetConnection);
  els.feedbackSearch.addEventListener("input", renderFeedback);

  document.querySelectorAll(".tab").forEach((button) => {
    button.addEventListener("click", () => switchTab(button.dataset.tab));
  });

  byId("resetMessageForm").addEventListener("click", resetMessageForm);
  els.messageForm.addEventListener("submit", saveMessage);
  els.versionForm.addEventListener("submit", saveVersion);
  els.configForm.addEventListener("submit", saveConfig);
}

function renderShell() {
  const configured = Boolean(state.settings.supabaseUrl && state.settings.supabaseAnonKey);
  const loggedIn = Boolean(state.session?.access_token);
  els.setupPanel.classList.toggle("hidden", configured);
  els.loginPanel.classList.toggle("hidden", !configured || loggedIn);
  els.appPanel.classList.toggle("hidden", !configured || !loggedIn);
  els.sessionLabel.textContent = loggedIn ? `已登录：${state.session.user?.email || "管理员"}` : "未登录";
  els.refreshBtn.disabled = !loggedIn;
  els.logoutBtn.disabled = !loggedIn;
  els.configBtn.disabled = !configured;
}

async function login(email, password) {
  const response = await fetch(`${state.settings.supabaseUrl}/auth/v1/token?grant_type=password`, {
    method: "POST",
    headers: baseHeaders(),
    body: JSON.stringify({ email, password }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throwToast(data.error_description || data.msg || "登录失败");
  state.session = normalizeSession(data);
  saveJson(SESSION_KEY, state.session);
  toast("登录成功");
  renderShell();
  await loadDashboard();
}

function logout() {
  state.session = null;
  localStorage.removeItem(SESSION_KEY);
  state.dashboard = null;
  renderShell();
  toast("已退出");
}

function resetConnection() {
  if (!confirm("确认恢复默认 Supabase 连接并退出当前登录？")) return;
  state.settings = DEFAULT_SETTINGS;
  state.session = null;
  state.dashboard = null;
  saveJson(STORAGE_KEY, state.settings);
  localStorage.removeItem(SESSION_KEY);
  byId("supabaseUrl").value = state.settings.supabaseUrl;
  byId("supabaseAnonKey").value = state.settings.supabaseAnonKey;
  renderShell();
  toast("已恢复默认连接");
}

async function loadDashboard() {
  if (!isReady()) return;
  try {
    state.dashboard = await adminAction("dashboard", {});
    renderDashboard();
    toast("数据已刷新");
  } catch (error) {
    toast(error.message || "读取失败", true);
  }
}

function renderDashboard() {
  const data = state.dashboard || {};
  const feedback = data.feedback || [];
  byId("feedbackCount").textContent = feedback.length;
  byId("newFeedbackCount").textContent = feedback.filter((item) => item.status === "new").length;
  byId("messageCount").textContent = (data.messages || []).length;
  byId("versionCount").textContent = (data.versions || []).length;
  renderFeedback();
  renderMessages();
  renderVersions();
  renderConfig();
}

function renderFeedback() {
  const keyword = els.feedbackSearch.value.trim().toLowerCase();
  const rows = (state.dashboard?.feedback || []).filter((item) => {
    const haystack = `${item.email || ""} ${item.content || ""} ${item.status || ""}`.toLowerCase();
    return !keyword || haystack.includes(keyword);
  });
  els.feedbackRows.innerHTML = rows.map((item) => `
    <tr>
      <td>${formatTime(item.created_at)}</td>
      <td>${escapeHtml(item.email || "-")}</td>
      <td class="content-cell">${escapeHtml(item.content || "")}</td>
      <td><span class="status">${statusLabel(item.status)}</span></td>
      <td>
        <select data-feedback-status="${item.id}">
          ${["new", "triaged", "done", "ignored"].map((value) => `<option value="${value}" ${item.status === value ? "selected" : ""}>${statusLabel(value)}</option>`).join("")}
        </select>
      </td>
    </tr>
  `).join("") || `<tr><td colspan="5">暂无反馈</td></tr>`;
  document.querySelectorAll("[data-feedback-status]").forEach((select) => {
    select.addEventListener("change", async () => {
      await adminAction("feedback.updateStatus", { id: select.dataset.feedbackStatus, status: select.value });
      await loadDashboard();
    });
  });
}

function renderMessages() {
  const list = byId("messageList");
  list.innerHTML = (state.dashboard?.messages || []).map((item) => `
    <article class="item">
      <div class="item-head">
        <div>
          <div class="item-title">${escapeHtml(item.title)}</div>
          <div class="item-meta">${formatTime(item.created_at)} · ${escapeHtml(item.source_key || "manual")}</div>
        </div>
      </div>
      <p>${escapeHtml(item.body).replace(/\n/g, "<br>")}</p>
      <div class="item-actions">
        <button class="small ghost" data-edit-message="${item.id}" type="button">编辑</button>
        <button class="small ghost danger" data-delete-message="${item.id}" type="button">删除</button>
      </div>
    </article>
  `).join("") || `<p class="muted">暂无消息</p>`;
  bindMessageActions();
}

function bindMessageActions() {
  document.querySelectorAll("[data-edit-message]").forEach((button) => {
    button.addEventListener("click", () => {
      const item = state.dashboard.messages.find((row) => row.id === button.dataset.editMessage);
      if (!item) return;
      byId("messageId").value = item.id;
      byId("messageTitle").value = item.title || "";
      byId("messageBody").value = item.body || "";
      byId("messageSourceKey").value = item.source_key || "";
      switchTab("messages");
    });
  });
  document.querySelectorAll("[data-delete-message]").forEach((button) => {
    button.addEventListener("click", async () => {
      if (!confirm("确认删除这条消息？")) return;
      await adminAction("message.delete", { id: button.dataset.deleteMessage });
      await loadDashboard();
    });
  });
}

async function saveMessage(event) {
  event.preventDefault();
  await adminAction("message.upsert", {
    id: byId("messageId").value || undefined,
    title: byId("messageTitle").value.trim(),
    body: byId("messageBody").value.trim(),
    source_key: byId("messageSourceKey").value.trim() || undefined,
  });
  resetMessageForm();
  await loadDashboard();
}

function resetMessageForm() {
  els.messageForm.reset();
  byId("messageId").value = "";
}

function renderVersions() {
  const list = byId("versionList");
  list.innerHTML = (state.dashboard?.versions || []).map((item) => `
    <article class="item">
      <div class="item-head">
        <div>
          <div class="item-title">v${escapeHtml(item.version_name)} (${item.version_code})</div>
          <div class="item-meta">${item.active ? "启用" : "停用"} · ${item.is_mandatory ? "强制更新" : "普通更新"} · ${formatTime(item.published_at)}</div>
        </div>
      </div>
      <p>${escapeHtml(item.release_notes || "").replace(/\n/g, "<br>")}</p>
      <div class="item-actions">
        <button class="small ghost" data-edit-version="${item.version_code}" type="button">填入表单</button>
      </div>
    </article>
  `).join("") || `<p class="muted">暂无版本</p>`;
  document.querySelectorAll("[data-edit-version]").forEach((button) => {
    button.addEventListener("click", () => fillVersion(button.dataset.editVersion));
  });
}

function fillVersion(versionCode) {
  const item = state.dashboard.versions.find((row) => String(row.version_code) === String(versionCode));
  if (!item) return;
  byId("versionCode").value = item.version_code || "";
  byId("versionName").value = item.version_name || "";
  byId("apkUrl").value = item.apk_url || "";
  byId("backupApkUrl").value = item.backup_apk_url || "";
  byId("sha256").value = item.sha256 || "";
  byId("fileSizeBytes").value = item.file_size_bytes || "";
  byId("releaseNotes").value = item.release_notes || "";
  byId("mandatory").checked = Boolean(item.is_mandatory);
  byId("active").checked = item.active !== false;
  switchTab("versions");
}

async function saveVersion(event) {
  event.preventDefault();
  await adminAction("version.upsert", {
    version_code: Number(byId("versionCode").value),
    version_name: byId("versionName").value.trim(),
    apk_url: byId("apkUrl").value.trim(),
    backup_apk_url: byId("backupApkUrl").value.trim() || null,
    sha256: byId("sha256").value.trim(),
    file_size_bytes: Number(byId("fileSizeBytes").value),
    release_notes: byId("releaseNotes").value.trim(),
    is_mandatory: byId("mandatory").checked,
    active: byId("active").checked,
  });
  els.versionForm.reset();
  byId("active").checked = true;
  await loadDashboard();
}

function renderConfig() {
  const list = byId("configList");
  list.innerHTML = (state.dashboard?.config || []).map((item) => `
    <article class="item">
      <div class="item-head">
        <div>
          <div class="item-title">${escapeHtml(item.key)}</div>
          <div class="item-meta">${item.active ? "启用" : "停用"} · ${formatTime(item.updated_at)}</div>
        </div>
      </div>
      <pre>${escapeHtml(JSON.stringify(item.value, null, 2))}</pre>
      <p class="muted">${escapeHtml(item.description || "")}</p>
      <div class="item-actions">
        <button class="small ghost" data-edit-config="${item.key}" type="button">编辑</button>
        <button class="small ghost danger" data-delete-config="${item.key}" type="button">删除</button>
      </div>
    </article>
  `).join("") || `<p class="muted">暂无配置</p>`;
  bindConfigActions();
}

function bindConfigActions() {
  document.querySelectorAll("[data-edit-config]").forEach((button) => {
    button.addEventListener("click", () => {
      const item = state.dashboard.config.find((row) => row.key === button.dataset.editConfig);
      if (!item) return;
      byId("configKey").value = item.key;
      byId("configValue").value = JSON.stringify(item.value, null, 2);
      byId("configDescription").value = item.description || "";
      byId("configActive").checked = item.active !== false;
    });
  });
  document.querySelectorAll("[data-delete-config]").forEach((button) => {
    button.addEventListener("click", async () => {
      if (!confirm("确认删除这个配置？")) return;
      await adminAction("config.delete", { key: button.dataset.deleteConfig });
      await loadDashboard();
    });
  });
}

async function saveConfig(event) {
  event.preventDefault();
  let value;
  try {
    value = JSON.parse(byId("configValue").value);
  } catch {
    return toast("JSON 值格式不正确", true);
  }
  await adminAction("config.upsert", {
    key: byId("configKey").value.trim(),
    value,
    description: byId("configDescription").value.trim(),
    active: byId("configActive").checked,
  });
  els.configForm.reset();
  byId("configValue").value = "{}";
  byId("configActive").checked = true;
  await loadDashboard();
}

function renderLinks() {
  byId("linksGrid").innerHTML = links.map(([title, url, desc]) => `
    <a class="link-card" href="${url}" target="_blank" rel="noreferrer">
      <strong>${escapeHtml(title)}</strong>
      <span>${escapeHtml(desc)}</span>
    </a>
  `).join("");
}

function switchTab(tab) {
  state.activeTab = tab;
  document.querySelectorAll(".tab").forEach((button) => button.classList.toggle("active", button.dataset.tab === tab));
  document.querySelectorAll(".tab-panel").forEach((panel) => panel.classList.add("hidden"));
  byId(`${tab}Tab`).classList.remove("hidden");
}

async function adminAction(action, payload) {
  await ensureFreshSession();
  let response = await adminRequest(action, payload);
  if (response.status === 401) {
    await ensureFreshSession(true);
    response = await adminRequest(action, payload);
  }
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.error) {
    if (response.status === 401) expireSession();
    throw new Error(data.error || `请求失败 ${response.status}`);
  }
  return data;
}

async function adminRequest(action, payload) {
  return fetch(`${state.settings.supabaseUrl}/functions/v1/admin-console`, {
    method: "POST",
    headers: {
      ...baseHeaders(),
      Authorization: `Bearer ${state.session.access_token}`,
    },
    body: JSON.stringify({ action, ...payload }),
  });
}

async function ensureFreshSession(force = false) {
  if (!state.session?.access_token) throw new Error("请先登录");
  if (!force && !shouldRefreshSession()) return;
  if (!state.session.refresh_token) {
    expireSession();
    throw new Error("登录已过期，请重新登录");
  }

  const response = await fetch(`${state.settings.supabaseUrl}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST",
    headers: baseHeaders(),
    body: JSON.stringify({ refresh_token: state.session.refresh_token }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    expireSession();
    throw new Error(data.error_description || data.msg || "登录已过期，请重新登录");
  }
  state.session = normalizeSession({ ...state.session, ...data });
  saveJson(SESSION_KEY, state.session);
  renderShell();
}

function shouldRefreshSession() {
  const expiresAt = Number(state.session?.expires_at || 0);
  if (!expiresAt) return false;
  return expiresAt * 1000 <= Date.now() + 60_000;
}

function normalizeSession(session) {
  const expiresIn = Number(session.expires_in || 0);
  const expiresAt = Number(session.expires_at || 0) ||
    (expiresIn > 0 ? Math.floor(Date.now() / 1000) + expiresIn : 0);
  return { ...session, expires_at: expiresAt };
}

function expireSession() {
  state.session = null;
  state.dashboard = null;
  localStorage.removeItem(SESSION_KEY);
  renderShell();
}

function baseHeaders() {
  return {
    apikey: state.settings.supabaseAnonKey,
    "content-type": "application/json",
  };
}

function isReady() {
  return Boolean(state.settings.supabaseUrl && state.settings.supabaseAnonKey && state.session?.access_token);
}

function normalizeSupabaseUrl(value) {
  try {
    const url = new URL(String(value || "").trim());
    const hostAllowed = /^[a-z0-9-]+\.supabase\.co$/i.test(url.hostname);
    if (url.protocol !== "https:" || !hostAllowed) return "";
    return `${url.origin}`.replace(/\/$/, "");
  } catch {
    return "";
  }
}

function byId(id) {
  return document.getElementById(id);
}

function loadJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch {
    return fallback;
  }
}

function loadSettings() {
  const settings = loadJson(STORAGE_KEY, DEFAULT_SETTINGS);
  if (!settings.supabaseUrl || !settings.supabaseAnonKey) return DEFAULT_SETTINGS;
  return settings;
}

function saveJson(key, value) {
  localStorage.setItem(key, JSON.stringify(value));
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#039;",
  }[char]));
}

function formatTime(value) {
  const number = Number(value);
  if (!number) return "-";
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(number));
}

function statusLabel(status) {
  return {
    new: "新反馈",
    triaged: "处理中",
    done: "已处理",
    ignored: "忽略",
  }[status] || status || "-";
}

function toast(message, isError = false) {
  els.toast.textContent = message;
  els.toast.style.background = isError ? "var(--danger)" : "var(--ink)";
  els.toast.classList.remove("hidden");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => els.toast.classList.add("hidden"), 2600);
}

function throwToast(message) {
  toast(message, true);
  throw new Error(message);
}
