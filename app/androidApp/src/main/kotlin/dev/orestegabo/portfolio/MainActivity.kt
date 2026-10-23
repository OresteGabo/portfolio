package dev.orestegabo.portfolio

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.orestegabo.portfolio.investments.PlatformBuild

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformBuild.debug = BuildConfig.DEBUG
        enableEdgeToEdge()
        setContent { App() }
    }
}
