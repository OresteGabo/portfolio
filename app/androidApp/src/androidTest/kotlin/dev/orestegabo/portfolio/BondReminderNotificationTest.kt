package dev.orestegabo.portfolio

import android.app.Notification
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class BondReminderNotificationTest {
    @Test fun backgroundCardUpdatesEndsAndRespectsMonthOptOut() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.executeShellCommand("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
        val manager=context.getSystemService(NotificationManager::class.java)
        fun awaitState(description: String, predicate: () -> Boolean) {
            val deadline=System.currentTimeMillis()+5000
            while(System.currentTimeMillis()<deadline) {
                if(predicate())return
                Thread.sleep(50)
            }
            fail(description)
        }
        val month="2027-03"
        val end=System.currentTimeMillis()/1000+86400
        val data=mapOf("kind" to "subscriptions","month" to month,"calendarId" to "synthetic-bond-reminder",
            "notificationId" to "synthetic-open","stage" to "open","endsAt" to end.toString(),
            "title" to "March Treasury bond","dueDate" to "2027-03-19","body" to "Subscriptions are open.")
        try {
            BondReminderNotifications.reconcile(context,month)
            BondReminderNotifications.receive(context,data)
            awaitState("Opening card was not posted") { manager.activeNotifications.any { it.notification.channelId==BondReminderNotifications.CHANNEL } }
            val card=manager.activeNotifications.single { it.notification.channelId==BondReminderNotifications.CHANNEL }
            assertTrue(card.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
            assertTrue(card.notification.timeoutAfter in 86_390_000..86_400_000)
            BondReminderNotifications.receive(context,data+mapOf("stage" to "closing","body" to "Subscriptions close soon."))
            awaitState("Closing state was not posted") { manager.activeNotifications.any { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("close soon") } }
            val updated=manager.activeNotifications.single { it.notification.channelId==BondReminderNotifications.CHANNEL }
            assertEquals(card.id,updated.id)
            assertTrue(updated.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("close soon"))
            BondReminderNotifications.receive(context,data+mapOf("stage" to "closed","notificationId" to "synthetic-closed"))
            awaitState("Closed subscriptions must remove their live card") { manager.activeNotifications.none { it.id==card.id } }
            manager.cancelAll()
            awaitState("Test notifications did not clear") { manager.activeNotifications.isEmpty() }
            BondReminderNotifications.receive(context,data)
            awaitState("Live card did not restart for the fixture") { manager.activeNotifications.any { it.id==card.id } }
            BondReminderNotifications.reconcile(context,"")
            awaitState("Removing a reminder must clear its ongoing card") { manager.activeNotifications.isEmpty() }
        } finally { manager.cancelAll();BondReminderNotifications.reconcile(context,"") }
    }
}
