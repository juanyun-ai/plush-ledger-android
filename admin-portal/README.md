# 绒绒记账管理后台

这是一个可部署到 EdgeOne Pages 的静态管理后台。它不保存 Supabase `service_role`，敏感读写全部通过 `supabase/functions/admin-console` 完成。

## 能做什么

- 查看用户在 App 内提交的反馈建议。
- 修改反馈状态：新反馈、处理中、已处理、忽略。
- 发布、编辑、删除官方消息。
- 创建或更新 Android 版本更新配置。
- 管理预留的远程配置项，供后续 App 版本读取。
- 汇总常用运维入口。

## 部署

当前 Supabase 侧已经完成：

- `app_config` 表已创建。
- `admin-console` Edge Function 已部署并为 ACTIVE，当前版本为 v3。
- 未登录访问会返回 401，非允许浏览器来源会返回 403。

EdgeOne Pages 侧：

1. 确认你的管理员账号满足以下任一条件：
   - `profiles.role = 'admin'`
   - 或 Edge Function Secret `ADMIN_EMAILS` 包含该账号邮箱。
   - 如果临时部署域名不是 `https://admin.xiaoxing.online`，需要把完整 Origin 加到 Edge Function Secret `ADMIN_ALLOWED_ORIGINS`。
2. 在 EdgeOne Pages 中创建静态站点：
   - GitHub 仓库：`juanyun-ai/plush-ledger-android`
   - 根目录：`admin-portal`
   - 构建命令：留空
   - 输出目录：留空或 `/`
3. 绑定 `admin.xiaoxing.online`。
4. 首次打开后台时填写 Supabase URL 和 anon key，它们会保存在当前浏览器本地。

如果 GitHub 授权不方便，可以直接把本目录打包成 ZIP 上传到 EdgeOne Pages。

## 安全边界

- 前端只使用 Supabase anon key 和用户登录 token。
- `SUPABASE_SERVICE_ROLE_KEY` 只放在 Supabase Edge Function Secrets。
- 管理函数会校验当前登录用户是否为管理员。
- 远程配置表 `app_config` 只适合放非敏感公开配置，不要放密钥。
