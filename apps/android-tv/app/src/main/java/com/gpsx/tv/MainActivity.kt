package com.gpsx.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gpsx.tv.ui.GpsxApp
import com.gpsx.tv.ui.GpsxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GpsxTheme {
                GpsxApp()
            }
        }
    }
}

