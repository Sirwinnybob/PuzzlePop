package com.family.puzzlepop.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The app's own language choice (English or Korean), independent of the system language.
 *
 * Android 13+ has a system per-app language ([LocaleManager]), which also shows in system settings.
 * Older versions don't, so there the choice is kept in plain SharedPreferences (it must be readable
 * synchronously in [wrap], before the activity has resources) and applied by wrapping the activity's
 * base context.
 */
object AppLanguage {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** The chosen language tag ("en", "ko"), or the system's language when nothing was chosen. */
    fun current(context: Context): String {
        val chosen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            saved(context)
        }
        return chosen ?: context.resources.configuration.locales[0].language
    }

    /** Switch language. Android 13+ restarts the screen itself; older versions need the recreate. */
    fun set(activity: Activity, tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).commit()
            activity.recreate()
        }
    }

    /** For an activity's attachBaseContext: applies the saved choice on Android 12 and older. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = saved(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(config)
    }

    private fun saved(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
}
