package io.github.adgjmptw2.diet.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.adgjmptw2.diet.R
import io.github.adgjmptw2.diet.data.ProfileRepository
import io.github.adgjmptw2.diet.ui.home.HomeScreen
import io.github.adgjmptw2.diet.ui.meals.MealsScreen
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
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    tabs.forEachIndexed { i, tab ->
                        val selected = pager.currentPage == i
                        NavigationBarItem(
                            selected = selected,
                            onClick = { scope.launch { pager.animateScrollToPage(i) } },
                            icon = {
                                Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(24.dp))
                            },
                            label = {
                                Text(
                                    tab.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            beyondViewportPageCount = 1,
        ) { page ->
            when (tabs[page]) {
                Tab.HOME -> HomeScreen(onEditProfile = onEditProfile)
                Tab.RECORDS -> ComingSoon("주간 기록", "주마다 몇 kg 빠졌는지, 그 주 식단", Tab.RECORDS.icon)
                Tab.PROGRESS -> ComingSoon("성과", "체중 곡선, 배지", Tab.PROGRESS.icon)
                Tab.MEALS -> MealsScreen()
            }
        }
    }
}

@Composable
private fun ComingSoon(title: String, desc: String, @DrawableRes icon: Int) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineLarge)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("준비 중", style = MaterialTheme.typography.titleLarge)
                Text(
                    desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
