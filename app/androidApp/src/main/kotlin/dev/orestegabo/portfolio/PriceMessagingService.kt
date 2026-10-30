package dev.orestegabo.portfolio

import android.app.*
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.orestegabo.portfolio.investments.NativePushBridge

class PriceMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        // Tokens are device-specific, never credentials. The authenticated app links them to its account.
        getSharedPreferences("portfolio.push",MODE_PRIVATE).edit().putString("token",token).apply()
    }
    override fun onMessageReceived(message: RemoteMessage) {
        Handler(Looper.getMainLooper()).post { NativePushBridge.priceUpdate() }
        if(!NotificationManagerCompat.from(this).areNotificationsEnabled())return
        val open=Intent(this,MainActivity::class.java).putExtra("stockNotifications",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(this,"price_changes")
            .setSmallIcon(R.drawable.ic_price_notification).setContentTitle(message.notification?.title ?: getString(R.string.app_name))
            .setContentText(message.notification?.body ?: "Open the app to see your price updates.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).setContentIntent(pending).build()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Permission can still be revoked between this check and delivery.
        runCatching { NotificationManagerCompat.from(this).notify(message.data["notificationId"]?.hashCode() ?: 1,notification) }
    }
}
