package dev.orestegabo.portfolio

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import dev.orestegabo.portfolio.investments.HomeWidgetBridge
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal object NextWidgetStore {
    private const val PREFS = "portfolio.next-widget"
    private fun preferences(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun snapshot(context: Context): JSONObject = runCatching {
        JSONObject(preferences(context).getString("snapshot", "{}") ?: "{}")
    }.getOrElse { JSONObject() }

    fun save(context: Context, value: String?) {
        val prefs = preferences(context)
        if (value == null) prefs.edit().clear().apply()
        else {
            if (prefs.getString("snapshot", null) == value) return
            prefs.edit().putString("snapshot", value).putLong("updated", System.currentTimeMillis()).apply()
        }
        updateAll(context)
    }

    fun onPush(context: Context, data: Map<String,String>) {
        // Only a known, cached public issue may be updated. Synthetic/unknown pushes add no widget data.
        if (data["stage"] != "form") { updateAll(context); return }
        val snapshot = snapshot(context)
        val issues = snapshot.optJSONArray("issues") ?: return
        for (i in 0 until issues.length()) {
            val issue = issues.optJSONObject(i) ?: continue
            if (issue.optString("id") == data["calendarId"] && issue.optString("close") == data["dueDate"]) {
                issue.put("form", true); save(context, snapshot.toString()); return
            }
        }
    }

    fun install(context: Context) {
        val app = context.applicationContext
        HomeWidgetBridge.install({ save(app, it) }, { medium ->
            val manager = AppWidgetManager.getInstance(app)
            if (Build.VERSION.SDK_INT >= 26 && manager.isRequestPinAppWidgetSupported) {
                manager.requestPinAppWidget(component(app, medium), null, null)
            } else Toast.makeText(app, "Long-press your home screen, choose Widgets, then Struct Capital.", Toast.LENGTH_LONG).show()
        })
    }

    private fun component(context: Context, medium: Boolean) = ComponentName(context,
        if (medium) NextMediumWidget::class.java else NextSmallWidget::class.java)

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (medium in listOf(false, true)) for (id in manager.getAppWidgetIds(component(context, medium)))
            manager.updateAppWidget(id, render(context, medium))
    }

    fun render(context: Context, medium: Boolean): RemoteViews {
        val snapshot = snapshot(context)
        val french = snapshot.optString("language", Locale.getDefault().language) == "fr"
        fun text(en: String, fr: String) = if (french) fr else en
        val locale = if (french) Locale.FRENCH else Locale.ENGLISH
        val today = LocalDate.now(ZoneId.of("Africa/Kigali"))
        val events = nextWidgetEvents(snapshot, today, french)
        val first = events.firstOrNull()
        fun pending(date: LocalDate?, request: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).putExtra("widgetDate", (date ?: today).toString())
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val view = RemoteViews(context.packageName, if (medium) R.layout.next_widget_medium else R.layout.next_widget_small)
        val format = DateTimeFormatter.ofPattern("d MMM", locale)
        view.setTextViewText(R.id.next_heading, text("What’s next", "À venir"))
        view.setTextViewText(R.id.next_status, first?.status ?: text("Your investment calendar", "Votre calendrier"))
        view.setTextViewText(R.id.next_title, first?.title ?: if (snapshot.has("issues")) text("No upcoming events", "Aucun événement à venir") else text("Open Struct Capital", "Ouvrir Struct Capital"))
        view.setTextViewText(R.id.next_date, first?.date?.format(format) ?: text("Calendar", "Calendrier"))
        view.setTextViewText(R.id.next_detail, first?.detail ?: text("Open the app to update", "Ouvrir l’app pour actualiser"))
        val updated = preferences(context).getLong("updated", 0)
        val saved = if (updated == 0L) text("No saved data", "Aucune donnée enregistrée") else {
            val stamp = Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM · HH:mm", locale))
            text("Saved $stamp", "Enregistré $stamp")
        }
        view.setTextViewText(R.id.next_updated, saved)
        view.setOnClickPendingIntent(android.R.id.background, pending(first?.date, if (medium) 7102 else 7101))
        view.setContentDescription(android.R.id.background,
            listOf(text("What’s next", "À venir"), first?.status, first?.title, first?.date?.toString(), first?.detail, saved).filterNotNull().joinToString(". "))
        if (medium) {
            val second = events.getOrNull(1)
            view.setTextViewText(R.id.next_second_label, text("ALSO COMING UP", "ÉGALEMENT À VENIR"))
            view.setTextViewText(R.id.next_second_title, second?.title ?: text("All upcoming events", "Tous les événements"))
            view.setTextViewText(R.id.next_second_date, second?.date?.format(format) ?: text("Calendar", "Calendrier"))
            view.setTextViewText(R.id.next_second_detail, second?.detail ?: text("Tap to explore", "Voir le calendrier"))
            view.setOnClickPendingIntent(R.id.next_secondary, pending(second?.date, 7103))
        }
        return view
    }
}

open class NextSmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = NextWidgetStore.updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = NextWidgetStore.updateAll(context)
}
class NextMediumWidget : NextSmallWidget()
