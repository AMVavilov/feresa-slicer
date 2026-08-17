// SPDX-License-Identifier: AGPL-3.0-only
package tech.g24.feresaslicer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tech.g24.feresaslicer.ui.FeresaSlicerApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FeresaSlicerApp()
        }
    }
}
