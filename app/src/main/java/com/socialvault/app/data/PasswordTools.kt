package com.socialvault.app.data

import java.security.SecureRandom

private val LOWER = "abcdefghjkmnpqrstuvwxyz"
private val UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ"
private val DIGITS = "23456789"
private val SYMBOLS = "!@#\$%^&*()-_=+"

/** Generate a strong random password (always includes lower, upper, digit, symbol). */
fun generatePassword(length: Int = 16): String {
    val random = SecureRandom()
    val all = LOWER + UPPER + DIGITS + SYMBOLS
    val chars = CharArray(length)
    // Guarantee one of each class, then fill the rest at random.
    chars[0] = LOWER[random.nextInt(LOWER.length)]
    chars[1] = UPPER[random.nextInt(UPPER.length)]
    chars[2] = DIGITS[random.nextInt(DIGITS.length)]
    chars[3] = SYMBOLS[random.nextInt(SYMBOLS.length)]
    for (i in 4 until length) {
        chars[i] = all[random.nextInt(all.length)]
    }
    // Shuffle so the guaranteed characters are not always first.
    for (i in chars.indices) {
        val j = random.nextInt(chars.size)
        val tmp = chars[i]
        chars[i] = chars[j]
        chars[j] = tmp
    }
    return String(chars)
}

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

/** Score a password from length + character variety. */
fun passwordStrength(password: String): PasswordStrength {
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.length >= 16) score++
    if (password.any { it.isLowerCase() }) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return when {
        score >= 5 -> PasswordStrength.STRONG
        score >= 3 -> PasswordStrength.MEDIUM
        else -> PasswordStrength.WEAK
    }
}

fun strengthLabel(strength: PasswordStrength): String = when (strength) {
    PasswordStrength.WEAK -> "Weak"
    PasswordStrength.MEDIUM -> "Medium"
    PasswordStrength.STRONG -> "Strong"
}
