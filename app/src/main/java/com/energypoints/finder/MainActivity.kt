package com.energypoints.finder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.energypoints.finder.ui.screens.PointFinderScreen
import com.energypoints.finder.ui.theme.EnergoPointsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EnergoPointsTheme {
                PointFinderScreen()
            }
        }
    }
}
