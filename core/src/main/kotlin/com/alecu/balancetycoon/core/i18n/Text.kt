package com.alecu.balancetycoon.core.i18n

/** Idiomas del juego. El español es el idioma de diseño; el inglés, el de la versión internacional. */
enum class Lang {
    ES, EN;

    companion object {
        /** Devuelve ES para cualquier variante del español ("es", "es-ES", "es-MX"...) y EN para el resto. */
        fun fromCode(code: String): Lang = if (code.lowercase().startsWith("es")) ES else EN
    }
}

/**
 * Texto bilingüe para el contenido del juego (nombres de cuentas, documentos, conceptos de asientos).
 * Los textos de la interfaz (botones, títulos) van en los strings.xml del módulo app.
 */
data class L(val es: String, val en: String) {
    fun get(lang: Lang): String = if (lang == Lang.ES) es else en
}
