import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

type CategoryInput = { name?: unknown; type?: unknown; parent?: unknown };

const jsonHeaders = { "Content-Type": "application/json; charset=utf-8" };

Deno.serve(async (request: Request) => {
  if (request.method !== "POST") return json({ error: "Method not allowed" }, 405);

  const authorization = request.headers.get("Authorization") ?? "";
  const accessToken = authorization.replace(/^Bearer\s+/i, "").trim();
  if (!accessToken) return json({ error: "Unauthorized" }, 401);

  const supabaseUrl = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const anonKey = Deno.env.get("SUPABASE_ANON_KEY");
  const aiBaseUrl = Deno.env.get("AI_BASE_URL")?.replace(/\/$/, "");
  const aiApiKey = Deno.env.get("AI_API_KEY");
  const aiModel = Deno.env.get("AI_MODEL");
  if (!supabaseUrl || !anonKey) return json({ error: "Server auth is not configured" }, 500);
  if (!aiBaseUrl || !aiApiKey || !aiModel) return json({ error: "AI 服务还未配置" }, 503);

  const userClient = createClient(supabaseUrl, anonKey);
  const { data, error } = await userClient.auth.getUser(accessToken);
  if (error || !data.user) return json({ error: "Invalid session" }, 401);

  const body = await safeJson(request);
  const text = stringValue(body.text, 160);
  const categories = normalizeCategories(body.categories);
  const accounts = normalizeStrings(body.accounts, 20, 24);
  if (!text || !categories.length) return json({ error: "Invalid request" }, 400);

  const upstream = await fetch(`${aiBaseUrl}/chat/completions`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${aiApiKey}`,
    },
    body: JSON.stringify({
      model: aiModel,
      temperature: 0.1,
      messages: [
        {
          role: "system",
          content: [
            "你是记账解析器。只输出一个 JSON 对象，不要 Markdown。",
            "顶层字段固定为 entries，entries 是 1-8 个账目的数组。数组每项字段固定为 type, amount_minor, category_name, category_parent, account_name, note, occurred_at。",
            "type 只能是 expense 或 income；amount_minor 必须是人民币分的正整数。",
            "category_name 与 category_parent 必须从用户提供的分类中选择；account_name 必须从用户账户中选择。",
            "用户明确点名分类、父分类或账户时必须优先采用，不得用模型猜测覆盖用户指令。",
            "准确解析任意日期，包括完整年月日、月日、昨天、前天、几天前、上周、本周和上个月；按 Asia/Shanghai 与 now 计算。",
            "仅有月日时必须使用 now 在 Asia/Shanghai 对应的当前年份，例如 now 为 2026 年时，6月7日就是 2026-06-07；绝不能凭空改成其他年份或月份。",
            "昨天必须是 now 在 Asia/Shanghai 的前一个自然日，前天必须是前两个自然日。",
            "房屋转租、收租、租金收入优先归入现有的房屋收入分类，不得归入兼职。",
            "occurred_at 必须是所识别日期时间的毫秒时间戳；只有文字完全没有日期线索时才返回 null。",
            "无法判断时选择最贴近的现有分类，不要创造分类。note 只保留商户、用途或物品等简短事实，不重复日期、金额和账户。",
            "一段话里存在两笔或更多金额且分别表达不同收支时，必须拆为多条 entries，保持原文本中的先后顺序；不要合并金额。",
          ].join("\n"),
        },
        {
          role: "user",
          content: JSON.stringify({
            text,
            now: new Date().toISOString(),
            timezone: "Asia/Shanghai",
            categories,
            accounts,
          }),
        },
      ],
    }),
  });

  if (!upstream.ok) return json({ error: "AI 服务暂时不可用" }, 502);
  const upstreamJson = await safeUpstreamJson(upstream);
  const content = upstreamJson?.choices?.[0]?.message?.content;
  const parsed = parseModelJson(content);
  if (!parsed) return json({ error: "AI 返回格式异常" }, 502);

  const candidateEntries = Array.isArray(parsed.entries) ? parsed.entries : [parsed];
  const entries = candidateEntries
    .slice(0, 8)
    .map((candidate) => normalizeEntry(candidate, categories, accounts, text))
    .filter((entry): entry is Record<string, unknown> => Boolean(entry));
  if (!entries.length) return json({ error: "未能识别有效金额" }, 422);
  return json({ entries });
});

function json(payload: Record<string, unknown>, status = 200): Response {
  return new Response(JSON.stringify(payload), { status, headers: jsonHeaders });
}

async function safeJson(request: Request): Promise<Record<string, unknown>> {
  try {
    const value = await request.json();
    return value && typeof value === "object" ? value as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

async function safeUpstreamJson(response: Response): Promise<any> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function normalizeCategories(value: unknown): Array<{ name: string; type: string; parent: string | null }> {
  if (!Array.isArray(value)) return [];
  return value.slice(0, 100).map((raw) => {
    const item = raw && typeof raw === "object" ? raw as CategoryInput : {};
    return {
      name: stringValue(item.name, 24),
      type: item.type === "income" ? "income" : "expense",
      parent: stringValue(item.parent, 24) || null,
    };
  }).filter((item) => item.name);
}

function normalizeStrings(value: unknown, maxItems: number, maxLength: number): string[] {
  if (!Array.isArray(value)) return [];
  return value.slice(0, maxItems).map((item) => stringValue(item, maxLength)).filter(Boolean);
}

function stringValue(value: unknown, maxLength: number): string {
  return typeof value === "string" ? value.trim().slice(0, maxLength) : "";
}

function parseModelJson(value: unknown): Record<string, unknown> | null {
  if (typeof value !== "string") return null;
  const cleaned = value.trim().replace(/^```(?:json)?/i, "").replace(/```$/, "").trim();
  try {
    const parsed = JSON.parse(cleaned);
    return parsed && typeof parsed === "object" ? parsed as Record<string, unknown> : null;
  } catch {
    return null;
  }
}

function normalizeEntry(
  value: unknown,
  categories: Array<{ name: string; type: string; parent: string | null }>,
  accounts: string[],
  fallbackText: string,
): Record<string, unknown> | null {
  const parsed = value && typeof value === "object" ? value as Record<string, unknown> : {};
  const type = parsed.type === "income" ? "income" : "expense";
  const amountMinor = toPositiveInt(parsed.amount_minor);
  if (!amountMinor) return null;
  const names = new Set(categories.filter((item) => item.type === type).map((item) => item.name));
  const parentNames = new Set(categories.filter((item) => item.type === type && !item.parent).map((item) => item.name));
  const categoryName = names.has(stringValue(parsed.category_name, 24)) ? stringValue(parsed.category_name, 24) : null;
  const categoryParent = parentNames.has(stringValue(parsed.category_parent, 24)) ? stringValue(parsed.category_parent, 24) : null;
  const accountName = accounts.includes(stringValue(parsed.account_name, 24)) ? stringValue(parsed.account_name, 24) : null;
  return {
    type,
    amount_minor: amountMinor,
    category_name: categoryName,
    category_parent: categoryParent,
    account_name: accountName,
    note: stringValue(parsed.note, 80) || fallbackText,
    occurred_at: toTimestamp(parsed.occurred_at),
  };
}

function toPositiveInt(value: unknown): number | null {
  const number = typeof value === "number" ? value : Number(value);
  return Number.isSafeInteger(number) && number > 0 && number <= 99_999_999_999 ? number : null;
}

function toTimestamp(value: unknown): number | null {
  const numeric = typeof value === "number" ? value : Number(value);
  if (Number.isSafeInteger(numeric) && numeric > 946_684_800_000 && numeric < 4_102_444_800_000) return numeric;
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return null;
}
