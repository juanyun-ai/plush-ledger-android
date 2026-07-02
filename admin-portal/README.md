# 绒绒记账管理后台

这是一个可部署到 Cloudflare Pages 或 EdgeOne Pages 的静态管理后台。它不保存 Supabase `service_role`，敏感读写全部通过 `supabase/functions/admin-console` 完成。

## 能做什么

- 查看用户在 App 和小程序内提交的反馈建议。
- 修改反馈状态：新反馈、处理中、已处理、忽略。
- 查看真实运营总览：App 用户、小程序用户、选日可证活跃、选日记账、反馈状态和用户明细。
- 用户明细按 App Auth 与小程序 mini_users 分开显示；小程序用户优先显示用户自设昵称，不做可能重复的合并。
- 运营总览支持选择日期，App 活跃只展示 `app_activity_events`、Auth 最后登录和 profile 设备同步能证明的数据，不把记账发生日伪装成登录。
- 可点开单个用户查看画像、反馈记录、近 7 日 / 近 30 日 / 近 12 月活动和记账趋势；没有事件流水的旧版本数据会明确标注证据边界。
- 发布、编辑、删除官方消息。
- 创建或更新 Android 版本更新配置。
- 管理预留的远程配置项，供后续 App 版本读取。
- 汇总常用运维入口。

## 部署

当前 Supabase 侧已经完成：

- `app_config` 和 `mini_feedback` 表已创建。
- `app_activity_events` 表已创建，用于后续 Android 版本记录轻量 App 打开事件。
- `admin-console` Edge Function 已部署并为 ACTIVE，当前版本为 v10。
- `wechat-mini-ledger` Edge Function 已部署并为 ACTIVE，当前版本为 v9，支持小程序反馈提交、远程官方消息、小程序昵称同步和 last_seen 更新。
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
- `support@xiaoxing.online` 当前 MX 指向阿里云 DirectMail，不应作为可靠收件箱使用。App/小程序内在线留言会写入 Supabase 反馈表，是当前主反馈通道；`2998319435@qq.com` 仅作备用收件箱。
