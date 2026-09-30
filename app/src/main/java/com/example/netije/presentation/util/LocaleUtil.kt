package com.example.netije.presentation.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.example.netije.domain.model.AppLanguage
import java.util.Locale

/**
 * SAFE for attachBaseContext. Returns a wrapped context that has the correct
 * Configuration applied, but preserves the base context chain so Hilt can still
 * find the Activity/Application.
 *
 * ⚠️ NEVER feed the return value of this function into
 *    CompositionLocalProvider(LocalContext provides wrappedContext)!
 *    It returns ContextImpl (not Activity) and will break Hilt's ViewModelFactory
 *    lookup with: Expected an activity context... but found ContextImpl.
 */
fun Context.wrapWithLocale(language: AppLanguage): Context {
    val locale = Locale(language.code)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(LocaleList(locale))
    } else {
        @Suppress("DEPRECATION")
        config.locale = locale
    }
    return createConfigurationContext(config)
}

/**
 * ⭐ RECOMMENDED for Compose reactive language switching.
 *
 * Updates the Activity's resources Configuration IN PLACE so that:
 *   1. LocalContext.current in Compose is STILL the Activity instance
 *      → Hilt finds ViewModelStoreOwner / HasDefaultViewModelProviderFactory ✅
 *   2. Resources.getString() / stringResource() return correct locale strings ✅
 *   3. DateFormat, NumberFormat pick up JVM default Locale (set inside) ✅
 *
 * Call this every time the selected language changes (in a LaunchedEffect
 * observing the settings flow) AND in onCreate before setContent.
 */
fun Activity.updateActivityLocale(language: AppLanguage) {
    val locale = Locale(language.code)
    Locale.setDefault(locale)

    val resources = this.resources
    val config = Configuration(resources.configuration)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        config.setLocales(LocaleList(locale))
    } else {
        @Suppress("DEPRECATION")
        config.locale = locale
    }
    config.setLayoutDirection(locale)
    @Suppress("DEPRECATION")
    resources.updateConfiguration(config, resources.displayMetrics)

    // Also apply to the Activity's base ConfigurationWindow frame for good measure.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        applyOverrideConfiguration(config)
    }
}

private fun Configuration.setLayoutDirection(locale: Locale) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        setLayoutDirection(locale)
    }
}

/** Unwrap helper — walks baseContext chain to find the real Activity if present. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
