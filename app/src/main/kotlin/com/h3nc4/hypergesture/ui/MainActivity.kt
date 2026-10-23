// Copyright (C) 2026  Henrique Almeida <me@h3nc4.com>
// Copyright (C) 2026  Victor Eduardo (@victoresidoro)
//
// This file is part of HyperGesture.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.

package com.h3nc4.hypergesture.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.h3nc4.hypergesture.R
import com.h3nc4.hypergesture.ui.theme.HyperGestureTheme
import com.h3nc4.hypergesture.update.UpdateViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 draws edge to edge anyway, and without this the status bar keeps white
        // icons on the light theme, so the clock and battery vanish.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HyperGestureTheme { HyperGestureApp() }
        }
    }
}

/** Every screen sits one level under home, so a list of them is the whole back stack. */
internal enum class Screen(@param:StringRes val title: Int) {
    HOME(R.string.app_name),
    SETUP(R.string.section_setup),
    APP_SWITCHING(R.string.section_app_switch),
    CIRCLE_TO_SEARCH(R.string.section_circle_to_search),
    UPDATES(R.string.section_updates),
    GESTURE_SETTINGS(R.string.section_gestures),
    TROUBLESHOOTING(R.string.section_troubleshooting),
    ABOUT(R.string.section_about),
}

private val BackStackSaver = Saver<List<Screen>, ArrayList<String>>(
    save = { stack -> ArrayList(stack.map { it.name }) },
    restore = { names -> names.map(Screen::valueOf) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HyperGestureApp() {
    val model = rememberAppModel()
    // Held here rather than in Updates. The daily check runs on app open and home reads its result.
    val updates: UpdateViewModel = viewModel()
    LifecycleResumeEffect(updates) {
        updates.onResume()
        onPauseOrDispose {}
    }
    var stack by rememberSaveable(stateSaver = BackStackSaver) { mutableStateOf(listOf(Screen.HOME)) }
    val screen = stack.last()
    val back = { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1, onBack = back)

    // Test tags become resource ids, so the e2e suites select rows without matching text.
    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            // The bar takes the screen background, matching the list below it.
            TopAppBar(
                title = { Text(stringResource(screen.title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = {
                    if (screen != Screen.HOME) {
                        IconButton(onClick = back, modifier = Modifier.testTag(TestTags.NAV_BACK)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_navigate_up),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(model.snackbar) },
    ) { padding ->
        // Keyed by screen. Each one opens at its top rather than at the previous scroll.
        key(screen) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                when (screen) {
                    Screen.HOME -> HomeScreen(model, updates) { stack = stack + it }
                    Screen.SETUP -> SetupScreen(model)
                    Screen.APP_SWITCHING -> AppSwitchingScreen(model)
                    Screen.CIRCLE_TO_SEARCH -> CircleToSearchScreen(model)
                    Screen.UPDATES -> UpdatesScreen(updates)
                    Screen.GESTURE_SETTINGS -> GestureSettingsScreen(model)
                    Screen.TROUBLESHOOTING -> TroubleshootingScreen(model)
                    Screen.ABOUT -> AboutScreen(model)
                }
            }
        }
    }
}
