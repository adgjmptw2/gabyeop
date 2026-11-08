package io.github.adgjmptw2.diet

import android.os.Bundle
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

    override fun onStart() {
        super.onStart()
        syncScheduler.onAppOpened()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateSystemBars(AppTheme.LIGHT)
        setContent {
            val vm: RootViewModel = hiltViewModel()
            val theme by vm.theme.collectAsStateWithLifecycle()
            SideEffect { updateSystemBars(theme) }
            DietTheme(theme = theme) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm = vm)
                }
            }
        }
    }

    private fun updateSystemBars(theme: AppTheme) {
        val style = if (theme.isDark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
