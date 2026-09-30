package com.example.netije

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.netije.domain.model.AppLanguage
import com.example.netije.di.AuthEventBus
import com.example.netije.presentation.component.NetijeBottomBar
import com.example.netije.presentation.navigation.BottomNavItem
import com.example.netije.presentation.navigation.NetijeNavHost
import com.example.netije.presentation.util.findActivity
import com.example.netije.presentation.util.updateActivityLocale
import com.example.netije.presentation.viewmodel.SettingsViewModel
import com.example.netije.ui.theme.NetijeTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import java.util.Locale
import android.widget.Toast

private const val TAG = "Netije_MainActivity"

/**
 * Composition local for reporting errors from any composable up to the
 * root-level error boundary. Use: LocalAppError.current.report(cause)
 */
internal data class AppError(
    val title: String,
    val cause: Throwable?,
    val stackTrace: String = cause?.let { runCatching { it.stackTraceToString() }.getOrDefault("") } ?: ""
) {
    val type: String get() = cause?.let { it::class.java.simpleName } ?: "(unknown)"
    val message: String get() = cause?.message ?: "(no message)"
}

internal fun interface AppErrorReporter {
    fun report(error: AppError)
}

internal val LocalAppError = staticCompositionLocalOf<AppErrorReporter> { AppErrorReporter { } }

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private fun applyLocaleNow(langCode: String) {
        val config = Configuration(resources.configuration)
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    override fun applyOverrideConfiguration(overrideConfiguration: Configuration?) {
        if (overrideConfiguration != null) {
            val uiMode = overrideConfiguration.uiMode
            overrideConfiguration.setTo(baseContext.resources.configuration)
            overrideConfiguration.uiMode = uiMode
        }
        super.applyOverrideConfiguration(overrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ══════════════════════════════════════════════════════════════════════
        // GLOBAL PROCESS CRASH CATCHER — prevents INSTANT "thrown back to launcher"
        // ══════════════════════════════════════════════════════════════════════
        // NOTE: This is in onCreate, NOT composable scope — try/catch here is
        // 100% valid because it's plain Android non-compose code.
        val originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "GLOBAL UNCAUGHT [thread=${thread.name}]", throwable)
            // Write to an in-memory cache that the Compose UI can read back
            LastCrashHolder.last = AppError("Global crash on thread ${thread.name}", throwable)
            // Try the system handler last — if OEM handler throws, swallow it.
            runCatching { originalHandler?.uncaughtException(thread, throwable) }
        }

        val startLang = runCatching {
            com.example.netije.data.local.datastore.SettingsDataStore(this).readLanguageSync()
        }.getOrDefault(com.example.netije.domain.model.AppLanguage.RU)
        Locale.setDefault(Locale(startLang.code))
        applyLocaleNow(startLang.code)
        enableEdgeToEdge()

        setContent {
            val pendingGlobal = remember { LastCrashHolder.last }
            var appError by remember { mutableStateOf<AppError?>(pendingGlobal) }

            // Install a composition-level error reporter for child composables
            val reporter = remember {
                AppErrorReporter { err -> appError = err }
            }
            CompositionLocalProvider(LocalAppError provides reporter) {
                if (appError != null) {
                    ErrorDisplayScreen(error = appError!!) {
                        // User clicks anywhere to retry (clears in-memory global crash cache)
                        LastCrashHolder.last = null
                        appError = null
                    }
                } else {
                    // ===== CORRECT PATTERN: NO try/catch around composable calls =====
                    // Use an App-level CoroutineExceptionHandler so any LaunchedEffect
                    // / ViewModel / flow crash goes to our reporter instead of force-close.
                    val ctxErrHandler = remember {
                        CoroutineExceptionHandler { _, throwable ->
                            Log.e(TAG, "Composition coroutine crashed", throwable)
                            reporter.report(AppError("Background operation failed", throwable))
                        }
                    }
                    val scope = rememberCoroutineScope { ctxErrHandler }
                    // Pass scope reference so any child composable could use it via rememberUpdatedState.
                    CompositionLocalProvider(
                        LocalCompositionExceptionHandler provides ctxErrHandler,
                    ) {
                        NetijeAppContent(
                            activity = this@MainActivity,
                            onError = { reporter.report(it) }
                        )
                    }
                }
            }
        }
    }
}

/** In-memory singleton for a global-crash → Compose UI handshake. */
private object LastCrashHolder {
    @Volatile var last: AppError? = null
}

/** Composition local to propagate a CoroutineExceptionHandler (non-throwing). */
private val LocalCompositionExceptionHandler = compositionLocalOf<CoroutineExceptionHandler> {
    CoroutineExceptionHandler { _, t -> Log.e(TAG, "Unhandled (no reporter)", t) }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ✅ REAL APP CONTENT — ZERO try/catch around any composable function calls
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun NetijeAppContent(
    activity: ComponentActivity,
    onError: (AppError) -> Unit
) {
    // Composable functions called DIRECTLY — no wrapping in try { }.
    // If any of these fail internally, the Compose snapshot system will propagate
    // to the CompositionExceptionHandler which is caught by our CEH above + global catcher.

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val showBottomBar = BottomNavItem.entries.any { it.route == currentDest?.route }

    // ==========================
    //  ViewModels (hiltViewModel)
    // ==========================
    // These are COMPOSABLE inline functions. NO try/catch.
    // If Hilt throws (ContextImpl / factory errors), the CompositionExceptionHandler
    // catches it, which we've routed to onError -> ErrorDisplayScreen.
    val settingsVm: SettingsViewModel = hiltViewModel()
    val context = LocalContext.current
    val sessionExpiredMsg = stringResource(R.string.session_expired)
    val sessionScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        AuthEventBus.sessionExpired.collect {
            Toast.makeText(context, sessionExpiredMsg, Toast.LENGTH_LONG).show()
            sessionScope.launch {
                runCatching { settingsVm.onSignOut() }
            }
        }
    }

    // Safe Flow collection: collectAsStateWithLifecycle is lifecycle-aware (pauses in bg)
    // and emits inside the composition's snapshot without crossing threads.
    val settingsState by settingsVm.state.collectAsStateWithLifecycle()

    // ===== SAFE LOCALE SWITCHING =====
    val desiredLang: AppLanguage = settingsState.settings.language

    // ⭐ SIDE EFFECTS ONLY IN LaunchedEffect (never during composition snapshot)
    // updateActivityLocale mutates resources.updateConfiguration + applyOverrideConfiguration on
    // the Activity in-place, so all Resources.getString() / stringResource() pick up the new
    // locale on next recomposition.
    LaunchedEffect(desiredLang) {
        val act = context.findActivity() ?: activity
        runCatching { act.updateActivityLocale(desiredLang) }
            .onFailure { t -> Log.w(TAG, "Locale apply failed", t) }
    }

    // Refresh LocalConfiguration only (NOT LocalContext!) to reflect the just-applied locale.
    // ⚠️ CRITICAL: We MUST NOT replace LocalContext with act.createConfigurationContext() — that
    // method returns ContextImpl (not Activity), which breaks Hilt's hiltViewModel() because
    // ViewModelProvider.Factory lookup expects LocalContext.current to implement
    // HasDefaultViewModelProviderFactory (i.e., ComponentActivity). Keeping LocalContext as the
    // ORIGINAL Activity instance preserves the Hilt/DI graph while still forcing stringResource
    // and date formatters to read the refreshed Locale via LocalConfiguration.
    val refreshedConfig = remember(desiredLang) {
        Configuration().also { c ->
            val l = Locale(desiredLang.code)
            Locale.setDefault(l)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                c.setLocales(android.os.LocaleList(l))
            } else {
                @Suppress("DEPRECATION")
                c.locale = l
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                c.setLayoutDirection(l)
            }
        }
    }

    // ===== THEME + APP UI =====
    // Only wrap LocalConfiguration — LocalContext remains the real @AndroidEntryPoint Activity.
    CompositionLocalProvider(
        LocalConfiguration provides refreshedConfig
    ) {
        NetijeTheme(darkMode = settingsState.settings.darkMode) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    if (showBottomBar) {
                        NetijeBottomBar(navController = navController)
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NetijeNavHost(navController = navController, settingsVm = settingsVm)
                }
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
//  ERROR DISPLAY SCREEN
// ══════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorDisplayScreen(
    error: AppError,
    onRetry: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = error.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    Text(
                        text = "Type: ${error.type}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                item {
                    Text(
                        text = "Message: ${error.message}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (error.stackTrace.isNotBlank()) {
                    items(error.stackTrace.lineSequence().toList()) { line ->
                        Text(
                            text = line,
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 18.dp))
                    androidx.compose.material3.Button(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text("Retry", fontWeight = FontWeight.SemiBold)
                    }
                }
                item {
                    Text(
                        text = "Tip: Long-press to copy these lines, or take a screenshot to report the bug.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
