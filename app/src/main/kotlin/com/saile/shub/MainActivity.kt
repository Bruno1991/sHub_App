package com.saile.shub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.saile.shub.ui.SHubApp
import com.saile.shub.ui.theme.SHubTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SHubTheme {
                SHubApp()
            }
        }
    }
}
