# AI 智能记账配置

绒绒记账的智能记账分为两层：

- **离线规则**：无需购买 API。用户输入或说出“午饭 28 元，用微信”即可识别金额、分类、账户和大致时间，文字不会离开手机。
- **云端模型**：仅在用户已登录云端账号且 Supabase Edge Function 配置完成后启用。它能更好理解自然表达，但用户仍必须确认后才能写入账本。

当前 APK 内没有内置大模型，也没有把 DeepSeek API Key 写进客户端。Android 端会先用 `LocalAiLedgerParser` 解析金额、日期、分类和账户；如果用户已登录、Supabase 已配置、`ai-ledger-parse` Edge Function 可用，才会再请求云端模型增强识别。模型返回失败、未配置或用户处于本地模式时，应用会继续使用离线规则，不影响记账。

## 安全边界

1. DeepSeek、小米等模型的 API Key **只放 Supabase Edge Function Secrets**，绝不能放进 APK、Git 仓库、CSV 或截图。
2. `ai-ledger-parse` 会先校验 Supabase 登录令牌；未登录、本地模式和无效令牌无法调用。
3. 只发送本次输入的短句、当前用户已有的分类名称和账户名称；不发送历史账目、用户邮箱、手机号、头像或完整个人资料。
4. 函数不记录用户输入、不把模型返回写入数据库；只有用户点击“确认记账”后，正常账目才会保存。

## 部署

在 Supabase Dashboard 的项目 **Edge Functions** 页面中部署 `ai-ledger-parse`，并设置以下 Secrets：

```text
AI_BASE_URL=https://你的模型服务 OpenAI 兼容根地址
AI_API_KEY=你的服务端 API Key
AI_MODEL=模型名称
```

函数会向 `${AI_BASE_URL}/chat/completions` 发起 OpenAI 兼容请求。DeepSeek 可使用其官方兼容接口；小米 API Plan 需先确认它是否提供相同的 Chat Completions 协议。若协议不同，只需要在该 Edge Function 内新增一个适配器，不必升级 APK，也不必暴露密钥。

DeepSeek 示例：

```text
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-chat
```

`AI_API_KEY` 使用 DeepSeek 控制台生成的服务端 Key，并放入 Supabase Edge Function Secrets。

## 为什么可能没有扣费

如果 DeepSeek 后台没有看到调用或扣费，通常不是因为 App 内置了别的模型，而是以下情况之一：

1. 用户使用的是本地模式，或没有 Supabase 登录会话，无法调用 `ai-ledger-parse`。
2. Supabase Edge Function 没有部署，或 `AI_BASE_URL`、`AI_API_KEY`、`AI_MODEL` 任一 Secret 缺失，函数会返回“AI 服务还未配置”。
3. Android 构建时没有配置 `SUPABASE_URL` 和 `SUPABASE_ANON_KEY`，客户端会自动退回本地规则。
4. 输入内容较简单，本地规则已经能解析；界面会显示“本次使用离线规则识别，不会上传文字”。
5. 测试次数很少且服务商有免费额度或账单延迟，控制台可能暂时看不到费用。

验证云端模型是否真的被调用，可以在 Supabase Edge Function Logs 里筛选 `ai-ledger-parse` 请求；如果界面提示“已由已配置的 AI 服务辅助识别”，才说明这次结果来自云端增强。

部署时应启用 JWT 校验。部署后，用已登录的测试账号输入“昨天奶茶 18 元，用支付宝”验证：系统应先展示识别结果，只有点“确认记账”才会写入账本。
