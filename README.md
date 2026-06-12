# 绒绒记账

一个中文、CNY、本地优先的 Android 记账应用。界面采用柔和的 3D 毛绒风格，云端账号支持跨设备同步，本地账号可完全离线使用。

## 当前版本

`v0.6.0`，Android 8.0 及以上。

## 当前交付

- 可本地真实存储收入、支出、转账、账户、分类、预算。
- 云端账号采用“邮箱验证码注册 + 设置密码”，之后直接使用邮箱和密码登录。
- 保留独立的本地模式；首次输入用户名和密码即创建本地账号，以后用相同凭据进入，但不能跨设备恢复。
- PIN 使用 PBKDF2 salted hash 保存，登录 token 使用 Android Keystore 加密。
- 支持可选隐私防截图、可选 PIN/生物识别解锁、CSV 导出、后台同步和 Supabase RLS。
- 已加入“我的”、个人资料、日夜模式、应用信箱、用户建议、永久会员入口和管理员铭牌。
- 支持启动时自动检查新版本，校验 SHA-256 后下载并交给 Android 系统安装器更新。
- 支持从系统相册选择头像；云端账号的头像保存在用户隔离的 Supabase 私有桶中。
- 当前已绑定 Supabase 项目：`plush-ledger` (`YOUR_PROJECT_ID`)。

## 数据保存逻辑

默认情况下，账目会先保存在手机本机的 Room 数据库里，所以断网也能记账。

用户完成邮箱注册并登录后会得到云端会话：

- 登录成功后，App 会先从云端拉取该用户已有数据。
- 新增、修改或删除账目后会触发联网同步，后台任务也会定期同步。
- 换手机时，只要用同一个邮箱登录，就会从云端恢复已经成功同步的数据。
- 本地模式不会上传；卸载、清除数据或更换手机会导致本地模式的数据丢失。

## 邮箱登录和云同步

1. Supabase 项目、Email provider 和自定义 SMTP 已启用。
2. 阿里云邮件推送负责向 QQ、163、Outlook 等邮箱发送验证码。
3. 发信子域名为 `mail.example.com`；火山引擎 DNS 中的 SPF、DKIM、DMARC 记录用于证明发信身份并降低进垃圾箱的概率。
4. Supabase 邮件模板使用 `{{ .Token }}` 显示数字验证码。
5. 新增云端结构位于 `supabase/migrations/20260612090000_account_profiles_mailbox.sql`。
6. 注销账号函数位于 `supabase/functions/delete-account/index.ts`，并已部署为 `delete-account`。
7. 在 `~/.gradle/gradle.properties` 或命令行加入：

```properties
SUPABASE_URL=https://你的项目.supabase.co
SUPABASE_ANON_KEY=你的publishable_key
```

项目使用 Supabase publishable key。它可以存在 APK 中，但所有数据表必须同时启用 RLS。严禁把 `service_role` key 写入 Android 工程、GitHub 或 APK。

## Supabase 控制台操作入口

当前项目控制台：

https://supabase.com/dashboard/project/YOUR_PROJECT_ID

Auth Providers：

https://supabase.com/dashboard/project/YOUR_PROJECT_ID/auth/providers

SMTP 配置入口通常在 Authentication 的 SMTP Settings：

https://supabase.com/dashboard/project/YOUR_PROJECT_ID/auth/smtp

阿里云邮件推送开通与域名配置：

https://help.aliyun.com/zh/direct-mail/getting-started/simplified-procedure-of-configuring-email-delivery

阿里云邮件推送计费说明：

https://help.aliyun.com/zh/direct-mail/billing-methods

手机短信入口目前不在 App 登录页展示。现有 Twilio 试用账号只能向已验证的测试手机号发送；若以后面向所有用户开放，需要升级付费账号并评估中国大陆短信送达、资费和合规要求。

## 用户数据保护清单

- Supabase 后台执行 `supabase/schema.sql` 后，每张表都启用 RLS，只允许用户访问自己的 `user_id` 数据。
- 手机本机 PIN 只保存 salted hash，不保存明文。
- 登录 token 使用 Android Keystore 加密保存。
- 防截图默认关闭，由用户在“我的 > 设置”中自行开启；Android Auto Backup 仍关闭，避免系统备份带走账本数据库。
- 不接广告 SDK，不接第三方统计 SDK，不在日志里写手机号、邮箱、金额和备注。
- 邮件验证码按钮有 60 秒倒计时；正式大规模开放前仍应配置 CAPTCHA、发送频率和公开隐私政策网页。

## 尚需外部资质的功能

- 微信/QQ 登录已经保留入口和绑定字段，但必须取得对应开放平台 AppID、配置签名并通过平台审核后才能真实启用。
- 微信/支付宝支付必须先开通商户号，并由服务端创建订单、验签和接收支付回调；当前按钮会明确提示通道未审核，不会产生扣款。
- 普通应用无法通过公开接口任意读取个人微信/支付宝消费记录。后续可合规实现账单文件导入，或在用户明确授权后解析本机支付通知。

## 信箱与管理员

- 官方消息保存在 Supabase 的 `official_messages` 表。
- 用户建议保存在 `feedback` 表，项目所有者可在 Supabase Table Editor 查看；目前不会自动转发到邮箱。
- 后台入口：打开 Supabase 项目，进入 `Table Editor`，选择 `feedback` 表即可查看邮箱、内容、提交时间和处理状态。
- `admin@example.invalid` 已在云端设置为 `admin` 和 `permanent`，客户端不能自行把普通账号升级为管理员或会员。

## 版本状态

`v0.6.0` 在 v0.5 基础上新增应用内更新、相册头像、独立账单页，并按 3D 毛绒参考图重构首页、记账、统计、我的和五项底部导航。

微信/QQ 登录、微信/支付宝支付和支付账单自动导入需要开放平台及商户资质，目前只保留入口和安全提示，不会伪造登录或扣款结果。

## 构建

```bash
./gradlew assembleDebug
```

输出 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

发布前执行：

```bash
./gradlew test lintDebug assembleDebug
```

## 项目结构

- `app/`：Android 客户端。
- `supabase/migrations/`：数据库结构和安全迁移。
- `supabase/functions/`：账号注销等云端函数。
- `CHANGELOG.md`：版本更新记录。

## 应用内更新

- 版本信息保存在 Supabase `app_versions` 表。
- APK 文件保存在公开的 `app-releases` Storage bucket；公开内容仅为安装包，不包含源码和用户数据。
- App 下载完成后会核对版本表中的 SHA-256，校验失败不会打开安装器。
- Android 不允许普通 App 静默安装，用户仍需在系统安装器中确认。
- 所有可覆盖安装的 APK 必须使用同一签名密钥；正式上架前需要改用长期保存的 release keystore。
