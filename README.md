<div align="center">
  <img src="app/src/main/res/drawable-nodpi/brand_logo.png" width="320" alt="绒绒记账 Logo" />

  # 绒绒记账

  **一款本地优先、可云同步的 3D 毛绒风 Android 记账应用**

  ![Version](https://img.shields.io/badge/version-0.7.0-FF9F2D?style=flat-square)
  ![Android](https://img.shields.io/badge/Android-8.0%2B-69C69E?style=flat-square)
  ![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-82AEE8?style=flat-square)
  ![License](https://img.shields.io/badge/license-PolyForm%20Noncommercial-EA7C73?style=flat-square)
</div>

> [!IMPORTANT]
> 本项目源码公开用于学习、研究和个人非商业用途。禁止将本项目、修改版本或衍生作品用于商业用途。详见 [LICENSE](LICENSE)。

## 功能

- 收入、支出、转账、分类、账户、预算与搜索。
- 参考图风格的四项底栏、独立记账页、日期分组账单和可交互日历。
- 环形图与柱状图并列统计，每页使用独立且与场景匹配的短标语。
- Room 本地优先存储，离线仍可正常记账。
- 邮箱验证码注册、密码登录和独立本地模式。
- 登录后使用 Supabase 同步账本、资料和私有头像。
- 稳定的本地头像缓存、昵称、年龄、生日、性别和邮箱/手机号换绑。
- 应用信箱、用户反馈和管理员/会员身份。
- PIN、生物识别、隐私防截图、数据导出和账号注销。
- 自动检查版本、校验 APK SHA-256 并交由 Android 系统安装器更新。

## 数据安全

- 金额使用最小货币单位整数保存，避免浮点误差。
- 所有用户数据表启用 RLS，用户只能访问自己的记录。
- 头像存储在用户隔离的私有 Storage bucket。
- PIN 只保存 salted hash；登录凭证由 Android Keystore 加密保护。
- 不集成广告、第三方埋点或分析 SDK，不在日志中记录账目明细。
- Supabase publishable key 可用于客户端，但严禁在 App 或仓库中加入 `service_role` 或 secret key。

## 本地运行

推荐使用 Android Studio 打开项目。首次构建前，在用户目录的 `~/.gradle/gradle.properties` 中配置自己的 Supabase 项目：

macOS 用户也可以双击仓库根目录的 `打开绒绒记账预览.command`。关键页面在 `UiPreviews.kt` 中提供了 Compose 预览，不启动虚拟手机也能查看；需要验证登录、数据库和页面跳转时再运行虚拟设备。

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your_publishable_key
```

也可以参考仓库里的 `gradle.properties.example`。本地 SDK 路径写入不提交 Git 的 `local.properties`。

```bash
./gradlew test lintDebug assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 云端结构

- `profiles`：头像、昵称和账号资料。
- `books`：账本。
- `accounts`：现金、银行卡、微信、支付宝等账户。
- `categories`：收入与支出分类。
- `transactions`：收入、支出和转账。
- `budgets`：月预算。
- `official_messages`：官方消息和版本更新说明。
- `feedback`：用户提交的建议。
- `app_versions`：应用内更新版本信息与 APK 校验值。

数据库结构和安全策略位于 `supabase/migrations/`。用户反馈可由项目所有者在自己的 Supabase Table Editor 中查看 `feedback` 表。

## 版本更新

每次发布新的 `app_versions` 记录时，数据库触发器会自动生成对应的官方信箱消息。APK 下载后必须通过版本表中的 SHA-256 校验，校验失败不会进入安装流程。

### v0.7.0

- 重做首页、账单、统计、记账和“我的”页面，品牌名统一为“绒绒记账”。
- 修复头像切换页面时短暂消失的问题。
- 新增年龄、生日、性别资料，以及邮箱和手机号换绑入口。
- 记账页改为独立页面，系统返回手势会回到上一页。

Android 不允许普通 App 静默安装，用户仍需要在系统安装器中确认。正式发行时请使用长期保存的 release keystore，后续版本必须保持相同签名。

## 尚未开放

微信/QQ 登录以及微信/支付宝支付需要开放平台 AppID、应用审核和商户资质。仓库只保留产品入口与安全提示，不伪造登录、支付或自动读取个人支付账单。

## 许可

[PolyForm Noncommercial License 1.0.0](LICENSE)。允许个人学习、研究、测试和其他非商业用途；商业授权需另行取得许可。
