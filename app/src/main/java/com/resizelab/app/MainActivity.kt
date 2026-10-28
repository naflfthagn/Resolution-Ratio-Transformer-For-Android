package com.resizelab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.resizelab.app.ui.EditorViewModel
import com.resizelab.app.ui.ResizeLabRoot
import com.resizelab.app.ui.theme.ResizeLabTheme

class MainActivity : ComponentActivity() {
    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ResizeLabTheme {
                ResizeLabRoot(viewModel = viewModel)
            }
        }
    }
}
