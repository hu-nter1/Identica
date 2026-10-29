package com.example.identica.security

object FieldMasker {

    private const val VISIBLE_TAIL = 3
    private const val MASK_CHAR = '*'

    fun maskNationalId(raw: String): String {
        val normalized = raw.filter { it.isLetterOrDigit() }.uppercase()
        if (normalized.length <= VISIBLE_TAIL) return MASK_CHAR.toString().repeat(normalized.length)
        val hidden = MASK_CHAR.toString().repeat(normalized.length - VISIBLE_TAIL)
        return hidden + normalized.takeLast(VISIBLE_TAIL)
    }
}
