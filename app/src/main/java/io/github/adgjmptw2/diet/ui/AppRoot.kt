package io.github.adgjmptw2.diet.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.ui.home.HomeScreen
import io.github.adgjmptw2.diet.ui.onboarding.OnboardingScreen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RootState { LOADING, NEEDS_SETUP, READY }

@HiltViewModel
class RootViewModel @Inject constructor(repo: ProfileRepository) : ViewModel() {
    val state: StateFlow<RootState> = repo.profile
        .map { if (it == null) RootState.NEEDS_SETUP else RootState.READY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.LOADING)
}

@Composable
fun AppRoot(vm: RootViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var editingProfile by rememberSaveable { mutableStateOf(false) }

    when (state) {
        RootState.LOADING -> Box(Modifier.fillMaxSize())
        RootState.NEEDS_SETUP -> OnboardingScreen(isEdit = false, onClose = {})
        RootState.READY -> if (editingProfile) {
            BackHandler { editingProfile = false }
            OnboardingScreen(isEdit = true, onClose = { editingProfile = false })
        } else {
            MainScaffold(onEditProfile = { editingProfile = true })
        }
    }
}

private enum class Tab(val label: String, @DrawableRes val icon: Int) {
    HOME("홈", R.drawable.ic_home),
    RECORDS("기록", R.drawable.ic_records),
    PROGRESS("성과", R.drawable.ic_chart),
    MEALS("식단", R.drawable.ic_meal),
}

@Composable
private fun MainScaffold(onEditProfile: () -> Unit) {
    val tabs = Tab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, tab ->
                    NavigationBarItem(
                        selected = pager.currentPage == i,
                        onClick = { scope.launch { pager.animateScrollToPage(i) } },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            beyondViewportPageCount = 1,
        ) { page ->
            when (tabs[page]) {
                Tab.HOME -> HomeScreen(onEditProfile = onEditProfile)
                Tab.RECORDS -> ComingSoon("주간 기록", "주마다 몇 kg 빠졌는지, 그 주 식단")
                Tab.PROGRESS -> ComingSoon("성과", "체중 곡선, 배지")
                Tab.MEALS -> ComingSoon("식단", "식단 기록, 칼로리 계산")
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, desc: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("준비 중", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
