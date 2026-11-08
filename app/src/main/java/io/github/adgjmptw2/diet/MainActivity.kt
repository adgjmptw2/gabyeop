package io.github.adgjmptw2.diet

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.adgjmptw2.diet.data.sync.SyncScheduler
import io.github.adgjmptw2.diet.ui.AppRoot
import io.github.adgjmptw2.diet.ui.RootViewModel
import io.github.adgjmptw2.diet.ui.theme.AppTheme
import io.github.adgjmptw2.diet.ui.theme.DietTheme
import javax.inject.Inject
import android.graphics.Color as AndroidColor

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var syncScheduler: SyncScheduler
    private var recordTodayRequest by mutableLongStateOf(0L)
    private var mealsRequest by mutableLongStateOf(0L)
    private var fastingRequest by mutableLongStateOf(0L)

    override fun onStart() {
        super.onStart()
        syncScheduler.onAppOpened()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordTodayRequest = savedInstanceState?.getLong("recordTodayRequest") ?: 0L
        mealsRequest = savedInstanceState?.getLong("mealsRequest") ?: 0L
        fastingRequest = savedInstanceState?.getLong("fastingRequest") ?: 0L
        consumeDestination(intent)
        updateSystemBars(AppTheme.LIGHT)
        setContent {
            val vm: RootViewModel = hiltViewModel()
            val theme by vm.theme.collectAsStateWithLifecycle()
            SideEffect { updateSystemBars(theme) }
            DietTheme(theme = theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm = vm, recordTodayRequest = recordTodayRequest, mealsRequest = mealsRequest, fastingRequest = fastingRequest)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeDestination(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("recordTodayRequest", recordTodayRequest)
        outState.putLong("mealsRequest", mealsRequest)
        outState.putLong("fastingRequest", fastingRequest)
        super.onSaveInstanceState(outState)
    }

    private fun consumeDestination(intent: Intent) {
        val serial = maxOf(System.currentTimeMillis(), recordTodayRequest + 1, mealsRequest + 1, fastingRequest + 1)
        if (intent.getBooleanExtra(EXTRA_RECORD_TODAY, false)) recordTodayRequest = serial
        if (intent.getBooleanExtra(EXTRA_MEALS, false)) mealsRequest = serial
        if (intent.getBooleanExtra(EXTRA_FASTING, false)) fastingRequest = serial
        intent.removeExtra(EXTRA_RECORD_TODAY)
        intent.removeExtra(EXTRA_MEALS)
        intent.removeExtra(EXTRA_FASTING)
    }

    companion object {
        const val EXTRA_RECORD_TODAY = "open-record-today"
        const val EXTRA_MEALS = "open-meals"
        const val EXTRA_FASTING = "open-fasting"
    }

    private fun updateSystemBars(theme: AppTheme) {
        val style = if (theme.isDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
