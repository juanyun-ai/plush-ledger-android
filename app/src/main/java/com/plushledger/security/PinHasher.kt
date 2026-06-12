package com.plushledger.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinHash(val salt: String, val hash: String)

object PinHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH = 256

    fun create(pin: String): PinHash {
        require(pin.length in 4..12)
        return createSecret(pin)
    }

    fun createPassword(password: String): PinHash {
        require(password.length in 6..64)
        return createSecret(password)
    }

    fun verify(pin: String, saltBase64: String, hashBase64: String): Boolean {
        val salt = Base64.getDecoder().decode(saltBase64)
        val expected = Base64.getDecoder().decode(hashBase64)
        val actual = pbkdf(pin, salt)
        return MessageDigest.isEqual(expected, actual)
    }

    fun stableLocalUserId(identifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(identifier.trim().lowercase().toByteArray(Charsets.UTF_8))
        return "local_" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun createSecret(secret: String): PinHash {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf(secret, salt)
        return PinHash(
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        )
    }
}
