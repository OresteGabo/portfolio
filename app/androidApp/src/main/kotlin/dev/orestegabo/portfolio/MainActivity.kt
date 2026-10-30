package dev.orestegabo.portfolio

import android.os.Bundle
import android.os.Build
import android.app.NotificationChannel
import android.app.NotificationManager
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import dev.orestegabo.portfolio.investments.NativePushBridge
import java.util.UUID
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.orestegabo.portfolio.investments.PlatformBuild
import dev.orestegabo.portfolio.investments.applyInitialCaptureProtection

class MainActivity : FragmentActivity() {
    private val pushPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { installPush() }
    private fun installPush() {
        val configured=FirebaseApp.getApps(this).isNotEmpty()
        val allowed=NotificationManagerCompat.from(this).areNotificationsEnabled()
        NativePushBridge.install(configured,allowed) {
            if(Build.VERSION.SDK_INT>=33 && !getSharedPreferences("portfolio.push",MODE_PRIVATE).getBoolean("asked",false)) {
                getSharedPreferences("portfolio.push",MODE_PRIVATE).edit().putBoolean("asked",true).apply()
                pushPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else if (Build.VERSION.SDK_INT >= 26) {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            } else {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        val prefs=getSharedPreferences("portfolio.push",MODE_PRIVATE)
        val id=prefs.getString("deviceId",null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("deviceId",it).apply() }
        NativePushBridge.identify(id,"android")
        if(!configured)return
        if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("price_changes",if(resources.configuration.locales[0].language=="fr") "Notifications du portefeuille" else "Portfolio updates",NotificationManager.IMPORTANCE_DEFAULT))
        prefs.getString("token",null)?.let { NativePushBridge.register(id,"android",it,"production") }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            prefs.edit().putString("token",token).apply();NativePushBridge.register(id,"android",token,"production")
        }
    }
    override fun onResume() { super.onResume();installPush() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);receivePdf(intent);if(intent.getBooleanExtra("stockNotifications",false) || intent.getStringExtra("route")=="stock-notifications")NativePushBridge.openInbox() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyInitialCaptureProtection(this)
        PlatformBuild.debug = BuildConfig.DEBUG
        PlatformBuild.apiOrigin = BuildConfig.DEVELOPMENT_API_ORIGIN
        enableEdgeToEdge()
        installPush()
        if(intent.getBooleanExtra("stockNotifications",false) || intent.getStringExtra("route")=="stock-notifications")NativePushBridge.openInbox()
        receivePdf(intent)
        setContent { App() }
        installExperimentalCaptureLayer(this)
    }
}
