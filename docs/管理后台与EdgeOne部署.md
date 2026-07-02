# 管理后台与静态站部署

## 当前采用方案

腾讯云 EdgeOne 如果使用中国大陆资源并绑定自定义域名，可能需要腾讯云接入备案。为避免备案阻塞，当前已改用 Cloudflare Pages 部署：

- 产品官网项目：`rongrong-privacy`
- 管理后台项目：`rongrong-admin`
- 产品官网预览域：`https://rongrong-privacy.pages.dev/`
- 管理后台预览域：`https://rongrong-admin.pages.dev/`

两个项目都已完成首次部署，并已在 Cloudflare Pages 添加自定义域名：

- `privacy.xiaoxing.online` -> `rongrong-privacy`
- `admin.xiaoxing.online` -> `rongrong-admin`

Cloudflare 当前等待 DNS 验证，提示 `CNAME record not set`。需要在火山引擎 DNS 中修改/新增：

```text
privacy  CNAME  rongrong-privacy.pages.dev
admin    CNAME  rongrong-admin.pages.dev
```

DNS 生效后，Cloudflare 会自动签发 HTTPS 证书。

## 推荐域名

- 产品官网：`privacy.xiaoxing.online`
- 产品官网别名：`rrjz.xiaoxing.online`
- 管理后台：`admin.xiaoxing.online`

`xiaoxing.onlie` 是拼写错误，实际域名应使用 `xiaoxing.online`。

`rongrong.xiaoxing.online` 疑似已经用于其他核心服务，本次不迁移、不复用，避免影响 App 同步、登录或 Supabase 相关链路。

`privacy.xiaoxing.online` 当前被 Android 1.0.5 用作隐私政策入口，App 内已有硬编码跳转。因此，官网主入口先继续使用 `privacy.xiaoxing.online`，不要贸然换成 `rrjz`。如果后续想让官网名字更像品牌，可以把 `rrjz.xiaoxing.online` 作为别名或跳转入口。

## 备案判断

EdgeOne Makers 创建项目时会选择加速区域。腾讯云文档说明，加速区域会影响添加自定义域名时是否需要备案。

- 如果选择中国大陆相关资源，并绑定 `xiaoxing.online` 的自定义域名，通常需要 ICP 备案。
- 如果域名已经在火山引擎等其他接入商完成备案，但现在要把网站托管/接入到腾讯云，腾讯云文档说明需要在腾讯云做接入备案。
- 如果选择不含中国大陆的全球区域，通常可以先减少备案阻塞，但国内访问体验可能不如大陆加速。

实操建议：

- 产品官网面向国内用户，长期方案是走腾讯云接入备案后绑定 `privacy.xiaoxing.online`。
- 管理后台只给开发者使用，可以先用 EdgeOne 预览域名，或选择不含中国大陆的区域绑定 `admin.xiaoxing.online`，不影响普通用户体验。
- 下载页暂不单独做；官网已有下载入口即可。

如果继续使用 Cloudflare Pages 或 Vercel 这类海外静态托管，通常不会触发腾讯云接入备案流程；但国内访问速度和稳定性取决于网络环境。

## 产品官网部署到 EdgeOne Pages

官网是静态页面，不需要 Java、Hono 或 Node 后端。

推荐用同一个 GitHub 仓库创建两个 EdgeOne Pages 项目，但分别选择不同根目录：

1. 打开腾讯云 EdgeOne Pages。
2. 新建项目，连接 GitHub 仓库 `juanyun-ai/plush-ledger-android`。
3. 构建设置：
   - 根目录：`docs`
   - 构建命令：留空
   - 输出目录：留空或 `/`
4. 绑定自定义域名：`privacy.xiaoxing.online`。
5. 在火山引擎 DNS 中把 `privacy` 的 CNAME 指向 EdgeOne 给出的目标地址。
6. 等 EdgeOne 签发 HTTPS 证书后，访问 `https://privacy.xiaoxing.online/` 验证。

如果 GitHub 授权暂时不方便，可以把 `docs/` 目录打包成 ZIP 后在 EdgeOne Pages 使用直接上传方式发布。

## 管理后台部署到 EdgeOne Pages

管理后台同样是静态页面，发布目录是 `admin-portal`。

当前已完成：

- 数据库迁移 `supabase/migrations/20260701093000_admin_console_config.sql` 已应用到 Supabase 线上项目。
- Supabase Edge Function `admin-console` 已部署，状态为 ACTIVE，当前版本为 v10。
- 未登录访问 `admin-console` 会返回 401。
- 浏览器跨域来源已收紧为 `https://admin.xiaoxing.online`、`https://rongrong-admin.pages.dev`、`http://localhost:4177`、`http://127.0.0.1:4177`，随机来源会返回 403。

上线管理后台页面：

1. 在 Supabase Edge Function Secrets 中确认：
   - `SUPABASE_URL`
   - `SUPABASE_ANON_KEY`
   - `SUPABASE_SERVICE_ROLE_KEY`
   - `ADMIN_EMAILS`：可选，用英文逗号分隔管理员邮箱，用于首次启动兜底。
   - `ADMIN_ALLOWED_ORIGINS`：可选；如果将后台部署到其他临时域名，需要把完整 Origin 加进去。
2. 在 EdgeOne Pages 新建第二个项目，连接 GitHub 仓库 `juanyun-ai/plush-ledger-android`。
3. 构建设置：
   - 根目录：`admin-portal`
   - 构建命令：留空
   - 输出目录：留空或 `/`
4. 绑定域名：`admin.xiaoxing.online`。
5. 打开后台后填写 Supabase URL 和 anon key，并使用管理员邮箱密码登录。

如果 GitHub 授权暂时不方便，可以把 `admin-portal/` 目录打包成 ZIP 后直接上传。管理后台目录内已包含 `edgeone.json`，会给后台页面加上 no-store、noindex 和基础安全响应头。

## 管理后台第一版功能

- 查看 App 用户反馈。
- 修改反馈状态：新反馈、处理中、已处理、忽略。
- 发布、编辑、删除官方消息。
- 创建或更新 Android 版本更新配置。
- 管理预留的远程配置项。
- 打开常用运维入口：Supabase、GitHub、EdgeOne、火山 DNS、微信公众平台、DeepSeek。

## 能远程影响当前 App 的内容

当前 Android 1.0.5 已经会读取：

- `official_messages`：影响 App 内通知/信箱。
- `app_versions`：影响更新弹窗、强制更新、下载地址和更新说明。

当前 Android 1.0.5 不会读取：

- `app_config` 远程配置。
- 远程主题色。
- 远程功能开关。
- 远程 UI 布局。

因此，管理后台现在可以实时影响消息和更新提示；要让公告弹窗、入口开关、支持链接、主题参数等配置实时生效，需要下一版 Android 新增读取 `app_config` 的逻辑。

## 安全边界

- 管理后台前端只保存 Supabase URL 和 anon key，这些属于公开客户端配置。
- `service_role` 只能放在 Supabase Edge Function Secrets，不能放入网页、APK、GitHub 或截图。
- `admin-console` 会校验登录用户是否为管理员。
- `admin-console` 会限制浏览器跨域来源；这不是权限本身，真正权限仍然依赖管理员登录和服务端校验。
- `app_config` 只适合保存公开配置，不要保存密钥、令牌或私人数据。

## AI 解析模型如何管理

Supabase 本身没有部署 AI 模型，当前架构是：

1. Android App 调用 Supabase Edge Function：`ai-ledger-parse`。
2. `ai-ledger-parse` 从 Supabase Edge Function Secrets 读取：
   - `AI_BASE_URL`
   - `AI_API_KEY`
   - `AI_MODEL`
3. 函数再调用对应模型服务的 `/chat/completions` 接口。

因此，具体调用 DeepSeek、小米还是其他服务商，取决于线上 Secrets 当前填的值。Secret 值不会出现在仓库、APK 或管理后台里，无法从代码直接看出来，这是正确的安全边界。

更换模型时，如果新服务兼容 OpenAI Chat Completions 协议，只需要改 Supabase Secrets：

```text
AI_BASE_URL=https://api.deepseek.com
AI_MODEL=deepseek-chat
AI_API_KEY=对应服务商的服务端 Key
```

如果新服务不是 OpenAI 兼容协议，则需要改 `supabase/functions/ai-ledger-parse/index.ts` 里的请求适配器，然后重新部署云函数；通常不需要升级 Android App。
