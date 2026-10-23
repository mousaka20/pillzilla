package app.pillzilla.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pillzilla.nfc.TagIo

sealed interface Screen {
    data object Home : Screen
    data class Edit(val reminderId: Int) : Screen
    data object Log : Screen
    data object Rewards : Screen
}

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    /** Bumped on every resume so permission banners re-check system settings. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PillzillaTheme {
                var screenKey by rememberSaveable { mutableStateOf("home") }
                val screen = screenFromKey(screenKey)
                val snackbar = remember { SnackbarHostState() }
                val message by viewModel.message.collectAsStateWithLifecycle()
                LaunchedEffect(message) {
                    message?.let {
                        viewModel.consumeMessage()
                        snackbar.showSnackbar(it)
                    }
                }
                BackHandler(enabled = screen != Screen.Home) {
                    viewModel.cancelAssign()
                    screenKey = "home"
                }
                Scaffold(
                    containerColor = PzColors.Ground,
                    snackbarHost = { SnackbarHost(snackbar) },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    val goHome = {
                        viewModel.cancelAssign()
                        screenKey = "home"
                    }
                    when (screen) {
                        Screen.Home -> HomeScreen(
                            viewModel = viewModel,
                            resumeTick = resumeTick,
                            onEdit = { screenKey = "edit:$it" },
                            onLog = { screenKey = "log" },
                            onRewards = { screenKey = "rewards" },
                            modifier = modifier,
                        )
                        is Screen.Edit -> EditReminderScreen(viewModel, screen.reminderId, onDone = goHome, modifier = modifier)
                        Screen.Log -> LogScreen(viewModel, onBack = goHome, onRewards = { screenKey = "rewards" }, modifier = modifier)
                        Screen.Rewards -> RewardsScreen(viewModel, onBack = goHome, modifier = modifier)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
        TagIo.enableReader(this) { viewModel.onTag(it) }
    }

    override fun onPause() {
        super.onPause()
        TagIo.disableReader(this)
    }

    private fun screenFromKey(key: String): Screen = when {
        key.startsWith("edit:") -> Screen.Edit(key.removePrefix("edit:").toInt())
        key == "log" -> Screen.Log
        key == "rewards" -> Screen.Rewards
        else -> Screen.Home
    }
}
