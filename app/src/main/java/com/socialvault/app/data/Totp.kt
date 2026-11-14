package com.socialvault.app.data

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * TOTP (RFC 6238) implemented by hand — Base32 decode + HMAC-SHA1 +
 * 30-second counter. No network, no extra dependency.
 * Returns the 6-digit code for [timeMillis], or null if the secret is invalid.
 */
fun totpCode(secret: String, timeMillis: Long): String? {
    val key = base32Decode(secret) ?: return null
    val counter = timeMillis / 30_000L
    val message = ByteBuffer.allocate(8).putLong(counter).array()
    val mac = Mac.getInstance("HmacSHA1")
    mac.init(SecretKeySpec(key, "HmacSHA1"))
    val hash = mac.doFinal(message)
    val offset = hash[hash.size - 1].toInt() and 0x0F
    val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
        ((hash[offset + 1].toInt() and 0xFF) shl 16) or
        ((hash[offset + 2].toInt() and 0xFF) shl 8) or
        (hash[offset + 3].toInt() and 0xFF)
    return String.format("%06d", binary % 1_000_000)
}

/** Seconds left before the current TOTP code expires (0..29). */
fun totpSecondsRemaining(timeMillis: Long): Long = 30L - (timeMillis / 1000L % 30L)

/** Decode a Base32 secret (RFC 4648 alphabet A-Z2-7, padding/spaces ignored). */
fun base32Decode(input: String): ByteArray? {
    val clean = input.trim().replace("=", "").replace(" ", "").uppercase()
    if (clean.isEmpty()) return null
    var buffer = 0
    var bitsLeft = 0
    val out = ArrayList<Byte>()
    for (ch in clean) {
        val value = when (ch) {
            in 'A'..'Z' -> ch - 'A'
            in '2'..'7' -> (ch - '2') + 26
            else -> return null
        }
        buffer = (buffer shl 5) or value
        bitsLeft += 5
        if (bitsLeft >= 8) {
            bitsLeft -= 8
            out.add(((buffer shr bitsLeft) and 0xFF).toByte())
        }
    }
    if (out.isEmpty()) return null
    return ByteArray(out.size) { out[it] }
}
