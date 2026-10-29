package com.example.identica.util

import java.io.File

object Validator {

    val SEX_OPTIONS: List<String> = listOf("Male", "Female", "Other")

    private val USERNAME_PATTERN = Regex("^[A-Za-z0-9_.]{3,32}$")
    private val NATIONAL_ID_PATTERN = Regex("""^(?:CM\d{5}[A-Z]{2}|CF\d{5}[A-Z]{2})$""")

    private const val MIN_PASSWORD_LENGTH = 10
    private const val MIN_AGE = 1
    private const val MAX_AGE = 120

    fun username(value: String): String? =
        if (USERNAME_PATTERN.matches(value)) {
            null
        } else {
            "Use 3-32 characters: letters, digits, '_' or '.'."
        }

    fun password(value: CharArray): String? {
        if (value.size < MIN_PASSWORD_LENGTH) return "Use at least $MIN_PASSWORD_LENGTH characters."
        if (value.none { it.isUpperCase() }) return "Include an uppercase letter."
        if (value.none { it.isLowerCase() }) return "Include a lowercase letter."
        if (value.none { it.isDigit() }) return "Include a digit."
        return null
    }

    fun fullName(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.length !in 2..80) return "Name must be 2-80 characters."

        val containsOnlyAllowedCharacters = trimmed.all { character ->
            character.isLetter() ||
                character == ' ' ||
                character == '-' ||
                character == '\'' ||
                character == '.'
        }

        return if (containsOnlyAllowedCharacters) {
            null
        } else {
            "Name contains invalid characters."
        }
    }

    fun age(value: String): String? {
        val parsed = value.trim().toIntOrNull() ?: return "Enter a whole number."
        return if (parsed in MIN_AGE..MAX_AGE) null else "Age must be between $MIN_AGE and $MAX_AGE."
    }

    fun sex(value: String?): String? =
        if (value != null && value in SEX_OPTIONS) null else "Select a sex."

    fun normalizeNationalId(raw: String): String {
        return raw
            .filter { character -> character.isLetterOrDigit() }
            .uppercase()
    }

    fun nationalId(value: String): String? =
        if (NATIONAL_ID_PATTERN.matches(normalizeNationalId(value))) {
            null
        } else {
            "ID must be in the form CM01234JK or CF01234JK."
        }

    fun documentDetails(value: String): String? =
        if (value.trim().length in 3..120) {
            null
        } else {
            "Document details must be 3-120 characters."
        }

    fun photo(file: File?): String? =
        if (file != null && file.exists() && file.length() > 0L) {
            null
        } else {
            "Capture a profile photo."
        }

    fun fingerprint(hash: String?): String? = null
}
