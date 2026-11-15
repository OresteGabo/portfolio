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
import android.view.View
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
            } else Toast.makeText(app, "Long-press your home screen, choose Widgets, then Inzira.", Toast.LENGTH_LONG).show()
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
        fun date(event: NextWidgetEvent?): String = event?.date?.format(
            DateTimeFormatter.ofPattern(if (event.date.year == today.year) "d MMM" else "d MMM yy", locale)) ?: ""
        view.setTextViewText(R.id.next_heading, text("What’s next", "À venir"))
        fun title(event: NextWidgetEvent?): String = when {
            event == null -> if (snapshot.has("issues")) text("Nothing upcoming", "Rien à venir") else text("Open Inzira", "Ouvrir Inzira")
            event.key.startsWith("payment:maturity:") -> text("Maturity", "Échéance")
            event.key.startsWith("payment:") -> "Coupon"
            else -> event.title
        }
        fun dateLabel(event: NextWidgetEvent?): String = when {
            event == null -> ""
            event.key.startsWith("payment:") -> text("Expected", "Prévu")
            event.priority == 0 && event.date == today -> text("Closes today", "Clôture aujourd’hui")
            event.priority == 0 -> text("Closes", "Clôture")
            else -> text("Opens", "Ouverture")
        }
        view.setTextViewText(R.id.next_status, dateLabel(first))
        view.setTextViewText(R.id.next_title, title(first))
        view.setTextViewText(R.id.next_date, date(first))
        view.setViewVisibility(R.id.next_status, if (first == null) View.GONE else View.VISIBLE)
        view.setViewVisibility(R.id.next_date, if (first == null) View.GONE else View.VISIBLE)
        val updated = preferences(context).getLong("updated", 0)
        val saved = if (updated == 0L) text("No saved data", "Aucune donnée enregistrée") else {
            val stamp = Instant.ofEpochMilli(updated).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM · HH:mm", locale))
            text("Saved $stamp", "Enregistré $stamp")
        }
        view.setOnClickPendingIntent(android.R.id.background, pending(first?.date, if (medium) 7102 else 7101))
        view.setContentDescription(android.R.id.background,
            listOf(text("What’s next", "À venir"), first?.status, first?.title, first?.date?.toString(), first?.detail, saved).filterNotNull().joinToString(". "))
        if (medium) {
            val second = events.getOrNull(1)
            view.setTextViewText(R.id.next_second_title, title(second))
            view.setTextViewText(R.id.next_second_status, dateLabel(second))
            view.setTextViewText(R.id.next_second_date, date(second))
            view.setViewVisibility(R.id.next_secondary, if (second == null) View.GONE else View.VISIBLE)
            view.setViewVisibility(R.id.next_divider, if (second == null) View.GONE else View.VISIBLE)
            view.setContentDescription(R.id.next_secondary,
                listOfNotNull(second?.status, second?.title, second?.date?.toString(), second?.detail).joinToString(". "))
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
