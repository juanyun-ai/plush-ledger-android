# 绒绒记账管理后台

这是一个可部署到 Cloudflare Pages 或 EdgeOne Pages 的静态管理后台。它不保存 Supabase `service_role`，敏感读写全部通过 `supabase/functions/admin-console` 完成。

## 能做什么

- 查看用户在 App 和小程序内提交的反馈建议。
- 修改反馈状态：新反馈、处理中、已处理、忽略。
- 查看真实运营总览：App 用户、小程序用户、今日登录、今日记账、反馈状态和用户明细。
- 用户明细按 App Auth 与小程序 mini_users 分开显示，不做可能重复的合并。
- 发布、编辑、删除官方消息。
- 创建或更新 Android 版本更新配置。
- 管理预留的远程配置项，供后续 App 版本读取。
- 汇总常用运维入口。

## 部署

当前 Supabase 侧已经完成：

- `app_config` 和 `mini_feedback` 表已创建。
- `admin-console` Edge Function 已部署并为 ACTIVE，当前版本为 v7。
- `wechat-mini-ledger` Edge Function 已部署并为 ACTIVE，当前版本为 v6，支持小程序反馈提交和远程官方消息。
- 未登录访问会返回 401，非允许浏览器来源会返回 403。

静态托管侧：

1. 确认你的管理员账号满足以下任一条件：
   - `profiles.role = 'admin'`
   - 或 Edge Function Secret `ADMIN_EMAILS` 包含该账号邮箱。
   - 如果临时部署域名不是 `https://admin.xiaoxing.online`，需要把完整 Origin 加到 Edge Function Secret `ADMIN_ALLOWED_ORIGINS`。
2. 在 Cloudflare Pages 或 EdgeOne Pages 中创建静态站点：
   - GitHub 仓库：`juanyun-ai/plush-ledger-android`
   - 根目录：`admin-portal`
   - 构建命令：留空
   - 输出目录：留空或 `/`
3. 绑定 `admin.xiaoxing.online`。
4. 首次打开后台时填写 Supabase URL 和 anon key，它们会保存在当前浏览器本地。

如果 GitHub 授权不方便，可以直接把本目录打包成 ZIP 上传到静态托管平台。

## 安全边界

- 前端只使用 Supabase publishable/anon key 和用户登录 token。
- `SUPABASE_SERVICE_ROLE_KEY` 只放在 Supabase Edge Function Secrets。
- 管理函数会校验当前登录用户是否为管理员。
- 远程配置表 `app_config` 只适合放非敏感公开配置，不要放密钥。
- 小程序反馈走 `mini_feedback` 表，用户账本快照仍保存在 `mini_ledger_snapshots`，两类数据不要混用。
- `support@xiaoxing.online` 当前 MX 指向阿里云邮箱。普通邮箱自动转发只能转到另一个邮箱地址，不能直接写入 Supabase；要自动入库，需要迁移/新增邮件路由服务，或使用能把原邮件推送到 HTTP Webhook 的邮件服务。
