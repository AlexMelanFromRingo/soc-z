package io.melan.socz

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DeveloperBoard
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.melan.socz.ui.screens.BatteryScreen
import io.melan.socz.ui.screens.CpuScreen
import io.melan.socz.ui.screens.DisplayScreen
import io.melan.socz.ui.screens.GpuScreen
import io.melan.socz.ui.screens.MemoryScreen
import io.melan.socz.ui.screens.NetworkScreen
import io.melan.socz.ui.screens.OverviewScreen
import io.melan.socz.ui.screens.SensorsScreen
import io.melan.socz.ui.screens.StorageScreen
import io.melan.socz.ui.theme.SocZTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SocZTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SocZApp()
                }
            }
        }
    }
}

private data class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector)

@Composable
private fun SocZApp() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    // Nine destinations is too many for a phone bottom bar — the five most useful
    // for "what's in this phone" get tabs: Overview, CPU, GPU, Memory, Battery.
    // Sensors / Storage / Display / Network sit on a secondary nav under Overview as cards.
    val tabs = listOf(
        Tab("overview", R.string.tab_overview, Icons.Outlined.Dashboard),
        Tab("cpu", R.string.tab_cpu, Icons.Outlined.DeveloperBoard),
        Tab("gpu", R.string.tab_gpu, Icons.Outlined.Videocam),
        Tab("memory", R.string.tab_memory, Icons.Outlined.Memory),
        Tab("battery", R.string.tab_battery, Icons.Outlined.BatteryFull),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = entry?.destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            if (current != tab.route) {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "overview",
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable("overview") { OverviewScreen(onOpenSecondary = { nav.navigate(it) }) }
            composable("cpu") { CpuScreen() }
            composable("gpu") { GpuScreen() }
            composable("memory") { MemoryScreen() }
            composable("battery") { BatteryScreen() }
            composable("sensors") { SensorsScreen() }
            composable("storage") { StorageScreen() }
            composable("display") { DisplayScreen() }
            composable("network") { NetworkScreen() }
        }
    }
}
