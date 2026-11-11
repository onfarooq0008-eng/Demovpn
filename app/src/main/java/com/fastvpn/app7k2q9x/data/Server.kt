package com.fastvpn.app7k2q9x.data

import java.util.Locale

data class Server(
    val name: String,
    val source: String,
    val ip: String,
    val country: String,
    val speedMbps: Double,
    val pingMs: Int?,
    val connectSeconds: Double?,
    val configUrl: String,
    val isAvailable: Boolean = true,
    val lastChecked: Long? = null,
) {
    val countryCode: String get() = CountryUtil.code(name, country)
    val flag: String get() = CountryUtil.flag(countryCode)
}

object CountryUtil {
    private val byName: Map<String, String> by lazy {
        Locale.getISOCountries().associateBy({ Locale("", it).getDisplayCountry(Locale.ENGLISH).lowercase() }, { it })
    }
    private val overrides = mapOf(
        "korea republic of" to "KR", "korea, republic of" to "KR", "russian federation" to "RU",
        "viet nam" to "VN", "iran, islamic republic of" to "IR", "taiwan" to "TW", "hong kong" to "HK",
        "united states of america" to "US", "united kingdom" to "GB", "turkey" to "TR", "czech republic" to "CZ",
    )

    fun code(serverName: String, country: String): String {
        val p = serverName.substringBefore('-')
        if (p.length == 2 && p.all { it.isLetter() }) return p.uppercase()
        val k = country.lowercase()
        return overrides[k] ?: byName[k] ?: ""
    }

    fun flag(code: String): String =
        if (code.length == 2 && code.all { it.uppercaseChar() in 'A'..'Z' })
            code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
        else "🌐"
}
