package com.example.identica.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {

    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    data class HashResult(val hash: String, val salt: String, val iterations: Int)

    fun hash(password: CharArray): HashResult {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        val derived = derive(password, salt, ITERATIONS)
        return HashResult(encode(derived), encode(salt), ITERATIONS)
    }

    fun verify(password: CharArray, expectedHash: String, salt: String, iterations: Int): Boolean {
        val derived = derive(password, decode(salt), iterations)
        return MessageDigest.isEqual(derived, decode(expectedHash))
    }

    fun burnTime(password: CharArray) {
        derive(password, ByteArray(SALT_BYTES), ITERATIONS)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)
}
