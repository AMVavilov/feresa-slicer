// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tech.g24.feresaslicer.modelimport.IncomingModelIntentRouter
import tech.g24.feresaslicer.ui.FeresaSlicerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            routeIncomingModel(intent)
        }
        setContent {
            FeresaSlicerApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeIncomingModel(intent)
    }

    private fun routeIncomingModel(intent: Intent?) {
        lifecycleScope.launch(Dispatchers.IO) {
            IncomingModelIntentRouter.route(this@MainActivity, intent)
        }
    }
}
