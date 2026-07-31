package com.herolens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.herolens.app.ui.HeroLensApp
import com.herolens.app.ui.theme.HeroLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HeroLensTheme {
                HeroLensApp()
            }
        }
    }
}
