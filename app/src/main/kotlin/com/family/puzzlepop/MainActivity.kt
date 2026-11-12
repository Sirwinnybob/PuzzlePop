package com.family.puzzlepop

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.family.puzzlepop.nav.AppNavHost
import com.family.puzzlepop.ui.theme.PuzzlePopTheme

// FragmentActivity (a ComponentActivity) because androidx.biometric's BiometricPrompt needs one.
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // system bar icons follow the app theme (dark icons on the light app)
        setContent {
            PuzzlePopTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
}
