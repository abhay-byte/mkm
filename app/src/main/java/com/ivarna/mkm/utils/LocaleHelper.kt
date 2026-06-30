package com.ivarna.mkm.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
    private const val PREFS_NAME = "locale_prefs"
    private const val KEY_LOCALE = "locale_code"

    fun getPersistedLocale(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LOCALE, "SYSTEM") ?: "SYSTEM"
    }

    fun persistLocale(context: Context, localeCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCALE, localeCode)
            .apply()
    }

    fun resolveLocale(localeCode: String): Locale {
        return when (localeCode) {
            "EN" -> Locale.forLanguageTag("en")
            "ZH_CN" -> Locale.forLanguageTag("zh-CN")
            else -> Locale.getDefault()
        }
    }

    fun wrap(context: Context, localeCode: String): Context {
        val locale = resolveLocale(localeCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        }
        context.resources.configuration.setLocale(locale)
        return context.createConfigurationContext(config)
    }

}
