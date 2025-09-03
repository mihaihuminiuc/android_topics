// Navigation Compose example (List -> Detail -> Settings)
// Requires: implementation "androidx.navigation:navigation-compose:<version>" and Jetpack Compose dependencies

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

// Simple data model
data class SampleItem(val id: Int, val title: String)

object Routes {
    const val LIST = "list"
    const val DETAIL = "detail"
    const val SETTINGS = "settings"
    const val ARG_ITEM_ID = "itemId"
}

class NavigationExampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavHost()
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            // Provide a sample list; in a real app this would come from a ViewModel
            ListScreen(
                items = List(20) { SampleItem(it, "Item #$it") },
                onItemClick = { id -> navController.navigate("${Routes.DETAIL}/$id") },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = "${Routes.DETAIL}/{${Routes.ARG_ITEM_ID}}",
            arguments = listOf(navArgument(Routes.ARG_ITEM_ID) { type = NavType.IntType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getInt(Routes.ARG_ITEM_ID) ?: 0
            DetailScreen(
                itemId = id,
                onBack = { navController.popBackStack() },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = {
                // Example: set a simple result on the previous back stack entry
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("refresh", true)
                navController.popBackStack()
            })
        }
    }
}

@Composable
fun ListScreen(items: List<SampleItem>, onItemClick: (Int) -> Unit, onOpenSettings: () -> Unit) {
    val scaffoldState = rememberScaffoldState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("List") }, actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            })
        },
        scaffoldState = scaffoldState
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            LazyColumn {
                items(items) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(item.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = item.title)
                    }
                }
            }
        }
    }
}

@Composable
fun DetailScreen(itemId: Int, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Detail #$itemId") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Details for item $itemId")
            Spacer(Modifier.height(8.dp))
            Button(onClick = onBack) { Text("Go Back") }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") }
        })
    }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text("Settings go here")
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Save & Back") }
        }
    }
}
