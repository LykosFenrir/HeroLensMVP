package com.herolens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.herolens.app.ui.HeroLensApp
import com.herolens.app.ui.theme.HeroLensTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeroLensTheme {
                HeroLensApp()
            }
        }
    }
}
