package com.example.identica.biometric

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToInt

object HashUtil {

    private const val HEX_DIGITS = "0123456789abcdef"

    fun sha256Hex(bytes: ByteArray): String =
        toHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return toHex(mac.doFinal(data))
    }

    fun toHex(bytes: ByteArray): String {
        val builder = StringBuilder(bytes.size * 2)
        for (byte in bytes) {
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    fun fromHex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Hex string must have an even length." }
        return ByteArray(hex.length / 2) { index ->
            hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}

object SimilarityCalculator {

    fun percent(hexA: String, hexB: String): Int {
        val first = HashUtil.fromHex(hexA)
        val second = HashUtil.fromHex(hexB)
        // Different-length descriptors are incompatible; never compare only a shared prefix.
        if (first.size == 0 || first.size != second.size) return 0
        val length = first.size
        var differingBits = 0
        for (index in 0 until length) {
            differingBits += Integer.bitCount((first[index].toInt() xor second[index].toInt()) and 0xFF)
        }
        val totalBits = length * 8
        return ((totalBits - differingBits) * 100.0 / totalBits).roundToInt()
    }
}

class FingerprintTemplateEngine(private val random: java.security.SecureRandom = java.security.SecureRandom()) {
    fun createTemplate(): String {
        val bytes = ByteArray(256)
        random.nextBytes(bytes)
        return HashUtil.sha256Hex(bytes)
    }

    fun createProbe(enrolledHashes: List<String>): String {
        if (enrolledHashes.isEmpty()) return createTemplate()
        val source = HashUtil.fromHex(enrolledHashes[random.nextInt(enrolledHashes.size)])
        val flips = 2 + random.nextInt(9)
        repeat(flips) {
            val bit = random.nextInt(source.size * 8)
            val index = bit / 8
            source[index] = (source[index].toInt() xor (1 shl (bit % 8))).toByte()
        }
        return HashUtil.toHex(source)
    }
}
