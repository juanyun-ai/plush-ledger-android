package com.plushledger.auth

import com.plushledger.security.PinHasher
import com.plushledger.sync.SupabaseClient

enum class AuthChannel { PHONE, EMAIL }

sealed class AuthOutcome {
    data class OtpSent(val message: String, val localFallbackCode: String? = null) : AuthOutcome()
    data class SignedIn(val session: UserSession, val message: String) : AuthOutcome()
    data class PasswordSetupRequired(val session: UserSession, val message: String) : AuthOutcome()
    data class Failed(val message: String) : AuthOutcome()
}

class AuthRepository(
    private val sessionStore: SessionStore,
    private val supabaseClient: SupabaseClient
) {
    fun signInLocal(username: String, password: String): AuthOutcome {
        val cleaned = username.trim()
        if (cleaned.length !in 2..32) return AuthOutcome.Failed("用户名需要 2-32 个字符")
        if (password.length !in 6..64) return AuthOutcome.Failed("密码需要 6-64 个字符")

        val exists = sessionStore.hasLocalPassword(cleaned)
        if (!exists) {
            sessionStore.saveLocalPassword(cleaned, password)
        } else if (!sessionStore.verifyLocalPassword(cleaned, password)) {
            return AuthOutcome.Failed("用户名或密码不正确")
        }

        val session = UserSession(
            userId = PinHasher.stableLocalUserId("local-account:$cleaned"),
            displayName = cleaned
        )
        sessionStore.saveSession(session)
        return AuthOutcome.SignedIn(
            session = session,
            message = if (exists) "已进入本地账本" else "本地账号已创建"
        )
    }

    suspend fun sendOtp(channel: AuthChannel, identifier: String, shouldCreateUser: Boolean): AuthOutcome {
        val cleaned = identifier.trim()
        if (cleaned.isBlank()) return AuthOutcome.Failed("请输入手机号或邮箱")
        if (channel == AuthChannel.EMAIL && !cleaned.isValidEmail()) {
            return AuthOutcome.Failed("请输入正确的邮箱地址")
        }
        return if (supabaseClient.isConfigured) {
            runCatching {
                supabaseClient.sendOtp(channel, cleaned, shouldCreateUser)
                AuthOutcome.OtpSent("验证码已发送")
            }.getOrElse { AuthOutcome.Failed(it.toFriendlyAuthMessage("验证码发送失败")) }
        } else {
            AuthOutcome.OtpSent("云端未配置，当前使用本地安全模式", "000000")
        }
    }

    suspend fun verifyOtp(
        channel: AuthChannel,
        identifier: String,
        token: String,
        requirePasswordSetup: Boolean
    ): AuthOutcome {
        val cleaned = identifier.trim()
        if (channel == AuthChannel.EMAIL && !cleaned.isValidEmail()) {
            return AuthOutcome.Failed("请输入正确的邮箱地址")
        }
        if (cleaned.isBlank() || token.trim().length < 4) return AuthOutcome.Failed("验证码不完整")
        return if (supabaseClient.isConfigured) {
            runCatching {
                val remote = supabaseClient.verifyOtp(channel, cleaned, token.trim())
                val session = UserSession(
                    userId = remote.userId,
                    displayName = maskIdentifier(cleaned),
                    accessToken = remote.accessToken,
                    refreshToken = remote.refreshToken,
                    phone = cleaned.takeIf { channel == AuthChannel.PHONE },
                    email = cleaned.takeIf { channel == AuthChannel.EMAIL }
                )
                sessionStore.saveSession(session)
                if (requirePasswordSetup) {
                    AuthOutcome.PasswordSetupRequired(session, "邮箱验证成功，请设置登录密码")
                } else {
                    AuthOutcome.SignedIn(session, "验证成功")
                }
            }.getOrElse { AuthOutcome.Failed(it.toFriendlyAuthMessage("验证码验证失败")) }
        } else {
            if (token.trim() != "000000") return AuthOutcome.Failed("本地模式验证码为 000000")
            val userId = PinHasher.stableLocalUserId("${channel.name}:$cleaned")
            val session = UserSession(
                userId = userId,
                displayName = maskIdentifier(cleaned),
                phone = cleaned.takeIf { channel == AuthChannel.PHONE },
                email = cleaned.takeIf { channel == AuthChannel.EMAIL }
            )
            sessionStore.saveSession(session)
            AuthOutcome.SignedIn(session, "已进入本地安全账本")
        }
    }

    suspend fun signInWithPassword(email: String, password: String): AuthOutcome {
        val cleaned = email.trim()
        if (!cleaned.isValidEmail()) return AuthOutcome.Failed("请输入正确的邮箱地址")
        if (password.length !in 8..64) return AuthOutcome.Failed("密码需要 8-64 个字符")
        if (!supabaseClient.isConfigured) return AuthOutcome.Failed("云端登录暂不可用，请使用本地模式")

        return runCatching {
            val remote = supabaseClient.signInWithPassword(cleaned, password)
            val session = UserSession(
                userId = remote.userId,
                displayName = maskIdentifier(cleaned),
                accessToken = remote.accessToken,
                refreshToken = remote.refreshToken,
                email = cleaned
            )
            sessionStore.saveSession(session)
            AuthOutcome.SignedIn(session, "登录成功")
        }.getOrElse { AuthOutcome.Failed(it.toFriendlyAuthMessage("登录失败")) }
    }

    suspend fun setRemotePassword(session: UserSession, password: String, confirmation: String): AuthOutcome {
        if (password != confirmation) return AuthOutcome.Failed("两次输入的密码不一致")
        if (password.length !in 8..64 || password.none(Char::isLetter) || password.none(Char::isDigit)) {
            return AuthOutcome.Failed("密码需为 8-64 位，并同时包含字母和数字")
        }
        val token = session.accessToken ?: return AuthOutcome.Failed("登录会话已失效，请重新验证邮箱")
        return runCatching {
            supabaseClient.updatePassword(token, password)
            AuthOutcome.SignedIn(session, "注册成功，已开启云同步")
        }.getOrElse { AuthOutcome.Failed(it.toFriendlyAuthMessage("密码设置失败")) }
    }

    suspend fun requestIdentityChange(channel: AuthChannel, identifier: String): AuthOutcome {
        val session = sessionStore.currentSession() ?: return AuthOutcome.Failed("请先登录云端账号")
        val token = session.accessToken ?: return AuthOutcome.Failed("本地模式不支持换绑")
        val cleaned = identifier.trim()
        if (channel == AuthChannel.EMAIL && !cleaned.isValidEmail()) return AuthOutcome.Failed("请输入正确的邮箱地址")
        if (channel == AuthChannel.PHONE && !cleaned.startsWith("+") ) return AuthOutcome.Failed("手机号请使用国际格式，例如 +8613800138000")
        return runCatching {
            supabaseClient.requestIdentityChange(token, channel, cleaned)
            AuthOutcome.OtpSent(if (channel == AuthChannel.EMAIL) "验证码已发送到新邮箱" else "验证码已发送到新手机号")
        }.getOrElse { AuthOutcome.Failed(it.toFriendlyAuthMessage("验证码发送失败")) }
    }

    suspend fun verifyIdentityChange(channel: AuthChannel, identifier: String, code: String): AuthOutcome {
        val session = sessionStore.currentSession() ?: return AuthOutcome.Failed("请先登录云端账号")
        val token = session.accessToken ?: return AuthOutcome.Failed("本地模式不支持换绑")
        val cleaned = identifier.trim()
        if (code.trim().length < 4) return AuthOutcome.Failed("验证码不完整")
        return runCatching {
            supabaseClient.verifyIdentityChange(token, channel, cleaned, code.trim())
            val identity = supabaseClient.fetchCurrentIdentity(token)
            val updated = session.copy(email = identity.email, phone = identity.phone)
            sessionStore.saveSession(updated)
            AuthOutcome.SignedIn(updated, if (channel == AuthChannel.EMAIL) "邮箱换绑成功" else "手机号换绑成功")
        }.getOrElse { AuthOutcome.Failed(it.toFriendlyAuthMessage("换绑验证失败")) }
    }

    fun signInWithPin(pin: String): AuthOutcome {
        val session = sessionStore.currentSession() ?: return AuthOutcome.Failed("还没有本地会话，请先用手机或邮箱进入")
        return if (sessionStore.verifyPin(pin)) {
            AuthOutcome.SignedIn(session, "解锁成功")
        } else {
            AuthOutcome.Failed("PIN 不正确")
        }
    }

    fun setPin(pin: String): AuthOutcome {
        if (pin.length !in 4..12 || pin.any { !it.isDigit() }) return AuthOutcome.Failed("PIN 需要 4-12 位数字")
        sessionStore.setPin(pin)
        val session = sessionStore.currentSession() ?: UserSession("local_guest", "本地账本")
        return AuthOutcome.SignedIn(session, "PIN 已设置")
    }

    fun signOut() = sessionStore.clearSession()

    private fun maskIdentifier(value: String): String {
        if (value.contains("@")) {
            val parts = value.split("@")
            val head = parts.first().take(2).padEnd(2, '*')
            return "$head***@${parts.getOrNull(1) ?: ""}"
        }
        return if (value.length <= 4) "本地账本" else value.take(3) + "****" + value.takeLast(2)
    }

    private fun Throwable.toFriendlyAuthMessage(fallback: String): String {
        val raw = message.orEmpty()
        return when {
            "429" in raw || raw.contains("rate", ignoreCase = true) -> "验证码请求太频繁了，请稍后再试；也可以先用本地模式进入"
            "403" in raw -> "验证码服务暂时不可用：请检查 Supabase Auth 登录方式和短信/邮件服务配置；也可以先用本地模式进入"
            "400" in raw && raw.contains("sms", ignoreCase = true) -> "短信服务还没配置好，请先配置 Supabase 短信服务商"
            "invalid login credentials" in raw.lowercase() -> "邮箱或密码不正确"
            "email not confirmed" in raw.lowercase() -> "邮箱还未完成验证"
            "user already registered" in raw.lowercase() -> "这个邮箱已经注册，请直接登录"
            "phone" in raw.lowercase() && "provider" in raw.lowercase() -> "短信服务还没配置好，请先配置 Supabase 短信服务商"
            raw.isBlank() -> fallback
            else -> raw.take(120)
        }
    }
}

private fun String.isValidEmail(): Boolean =
    length in 5..254 && matches(Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+$"))
