package dev.orestegabo.portfolio

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Stage progress, never investment completion or an inferred broker closing hour. */
internal data class BondReminderPresentation(
    val status: String,
    val remaining: String,
    val deadline: String,
    val progress: Int,
    val french: Boolean,
) {
    companion object {
        fun from(data: Map<String, String>, locale: Locale): BondReminderPresentation {
            val fr = locale.language == "fr"
            val stage = data["stage"]
            val status = when (stage) {
                "open" -> if (fr) "Souscription ouverte" else "Subscriptions open"
                "closing" -> if (fr) "Clôture imminente" else "Closing soon"
                "closed" -> if (fr) "Souscription clôturée" else "Subscriptions closed"
                else -> if (fr) "Formulaire disponible" else "Form available"
            }
            val date = runCatching { LocalDate.parse(data["dueDate"]) }.getOrNull()
            // A fixed date remains truthful across midnight without a background refresh service.
            val remaining = if (stage == "closed") { if (fr) "Terminée" else "Completed" }
                else date?.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)).orEmpty()
            val deadline = date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))?.let {
                if (fr) "Clôture le $it · heure du Rwanda" else "Closes $it · Rwanda time"
            }.orEmpty()
            return BondReminderPresentation(status, remaining, deadline,
                when (stage) { "open" -> 500; "closing" -> 900; "closed" -> 1000; else -> 0 }, fr)
        }
    }

    fun decorateLegacy(context: Context, builder: NotificationCompat.Builder, title: String) {
        val compact = RemoteViews(context.packageName, R.layout.bond_notification_compact).apply {
            setTextViewText(R.id.bond_title, title)
            setTextViewText(R.id.bond_summary, listOf(status, remaining).filter(String::isNotBlank).joinToString(" · "))
        }
        val expanded = RemoteViews(context.packageName, R.layout.bond_notification_expanded).apply {
            setTextViewText(R.id.bond_title, title)
            setTextViewText(R.id.bond_status, status)
            setTextViewText(R.id.bond_remaining, remaining)
            setTextViewText(R.id.bond_deadline, deadline)
            setProgressBar(R.id.bond_progress, 1000, progress, false)
            setContentDescription(R.id.bond_progress, status)
            setTextViewText(R.id.bond_form_step, if (french) "Formulaire" else "Form")
            setTextViewText(R.id.bond_open_step, if (french) "Ouverture" else "Open")
            setTextViewText(R.id.bond_close_step, if (french) "Clôture" else "Close")
        }
        builder.setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(compact).setCustomBigContentView(expanded)
    }

    @RequiresApi(36)
    fun decorateNative(context: Context, notification: Notification): Notification {
        val accent = context.getColor(R.color.bond_timeline_accent)
        val track = context.getColor(R.color.bond_timeline_track)
        val style = Notification.ProgressStyle()
            .setProgressSegments(listOf(
                Notification.ProgressStyle.Segment(500).setColor(accent),
                Notification.ProgressStyle.Segment(500).setColor(track)))
            .setProgressPoints(listOf(0, 500, 1000).map { Notification.ProgressStyle.Point(it).setColor(accent) })
            .setProgress(progress)
            .setProgressTrackerIcon(Icon.createWithResource(context, R.drawable.ic_price_notification))
        return Notification.Builder.recoverBuilder(context, notification)
            .setStyle(style)
            .setShortCriticalText(if (progress == 900) { if (french) "Clôture" else "Closing" } else { if (french) "Ouvert" else "Open" })
            // Public promotion extra; users and the OS retain control of promotion.
            .addExtras(android.os.Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
            .build()
    }
}
