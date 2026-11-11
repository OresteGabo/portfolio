package dev.orestegabo.portfolio

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/** A server-authorized month subscription; no background polling or permanent service. */
internal object BondReminderNotifications {
    const val CHANNEL = "bond_reminders"
    private fun liveId(issue: String) = "bond-live:$issue".hashCode()
    fun reconcile(context: Context, months: String) {
        val prefs = context.getSharedPreferences("portfolio.push", Context.MODE_PRIVATE)
        val selected = months.split(',').filter(String::isNotBlank).toSet()
        val editor = prefs.edit().putStringSet("bond.months", selected)
        for((key, value) in prefs.all) if(key.startsWith("bond.card.") && value !in selected) {
            NotificationManagerCompat.from(context).cancel(liveId(key.removePrefix("bond.card.")))
            editor.remove(key)
        }
        editor.apply()
    }
    fun receive(context: Context, data: Map<String,String>) {
        val issue = data["calendarId"].orEmpty()
        val month = data["month"].orEmpty()
        val stage = data["stage"].orEmpty()
        if(!issue.matches(Regex("[A-Za-z0-9_-]{1,120}")) || !month.matches(Regex("[0-9]{4}-(0[1-9]|1[0-2])"))) return
        val prefs = context.getSharedPreferences("portfolio.push", Context.MODE_PRIVATE)
        // The server owns month opt-in; this device’s cached choices may predate a change on another phone.
        val manager = NotificationManagerCompat.from(context)
        if(!manager.areNotificationsEnabled()) return
        if(Build.VERSION.SDK_INT >= 26) context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Bond reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val now = System.currentTimeMillis()
        val end = data["endsAt"]?.toLongOrNull()?.times(1000) ?: return
        if(stage != "closed" && end <= now) return
        val live = stage in listOf("open", "closing") && !prefs.getBoolean("bond.dismissed.$issue",false)
        if(stage == "closed") {
            manager.cancel(liveId(issue))
            prefs.edit().remove("bond.card.$issue").apply()
        }
        val open = Intent(context,MainActivity::class.java).putExtra("stockNotifications",true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(context,liveId(issue),open,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val dismiss = PendingIntent.getBroadcast(context,liveId(issue),Intent(context,BondReminderDismissReceiver::class.java)
            .putExtra("calendarId",issue),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val presentation = BondReminderPresentation.from(data, context.resources.configuration.locales[0])
        val text = data["body"].orEmpty() + if(live) "\n${presentation.deadline}" else ""
        val builder = NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_price_notification)
            .setContentTitle(data["title"].orEmpty()).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(context.getColor(R.color.bond_timeline_accent))
            .setSubText(presentation.status).setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(pending)
            .setAutoCancel(!live).setOngoing(live).setOnlyAlertOnce(false)
            .setTimeoutAfter(if(live) (end-now).coerceAtLeast(1) else 86400000)
        if(live) {
            builder.setDeleteIntent(dismiss)
                .addAction(0,if(presentation.french) "Voir les mises à jour" else "View updates",pending)
                .addAction(0,if(presentation.french) "Masquer le suivi" else "Stop live status",dismiss)
            if(Build.VERSION.SDK_INT < 36) presentation.decorateLegacy(context,builder,data["title"].orEmpty())
            else builder.setContentText(listOf(presentation.remaining,presentation.deadline).filter(String::isNotBlank).joinToString(" · "))
        }
        var notification = builder.build()
        if(live && Build.VERSION.SDK_INT >= 36) notification = presentation.decorateNative(context,notification)
        val id = if(live) liveId(issue) else data["notificationId"].orEmpty().hashCode()
        // Permission may be revoked after areNotificationsEnabled() was checked.
        try { manager.notify(id,notification) } catch (_: SecurityException) { return }
        if(live) prefs.edit().putString("bond.card.$issue",month).apply()
    }
    fun dismiss(context: Context, issue: String) {
        context.getSharedPreferences("portfolio.push", Context.MODE_PRIVATE).edit().putBoolean("bond.dismissed.$issue",true).apply()
        NotificationManagerCompat.from(context).cancel(liveId(issue))
    }
}

class BondReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("calendarId")?.let { BondReminderNotifications.dismiss(context,it) }
    }
}
