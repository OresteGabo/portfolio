@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.TimeSource

class NotificationCoroutineSafetyTest {
    private class Host(private val heldPath: String) : PortfolioNotificationHost {
        val reply = CompletableDeferred<JsonRecord>()
        val entered = CompletableDeferred<Unit>()
        var ignoresCancellation = false
        var invalidations = 0
        override val api = object : PortfolioDataApi {
            override suspend fun request(path: String, method: String, body: JsonRecord?): JsonRecord {
                if (path == heldPath) {
                    entered.complete(Unit)
                    return if (ignoresCancellation) withContext(NonCancellable) { reply.await() } else reply.await()
                }
                return when {
                    path.endsWith("preferences") -> JsonRecord().put("configured", true).put("categories", JsonRecord())
                    path.endsWith("coupons") -> JsonRecord().put("receipts", JsonList())
                    else -> error("Unexpected request: $path")
                }
            }
            override suspend fun bytes(path: String, method: String, body: JsonRecord?): ByteArray = error("Unexpected PDF")
        }
        override var user: JsonRecord? = JsonRecord().put("id", "old-account")
        override val locked = false
        override val isGuest = false
        override val monthlyInvestmentTarget = 100_000L
        override var error: String? = null
        override fun applyMonthlyTarget(amount: Long) = Unit
        override suspend fun invalidateRejectedSession(failure: Exception): Boolean {
            if (failure is ApiException && failure.status == 401) { invalidations++; user = null; return true }
            return false
        }
    }

    @Test fun oldPreferenceOrInboxFailureCannotRejectANewAccount() = runTest {
        for (settings in listOf(true, false)) {
            val host = Host(if (settings) "/api/bonds/notifications/preferences" else "/api/bonds/notifications")
            val notifications = PortfolioNotifications(this, TimeSource.Monotonic, host)
            val request = launch { if (settings) notifications.fetchNotificationSettings() else notifications.fetchNotifications() }
            host.entered.await()
            notifications.clear(); host.user = JsonRecord().put("id", "new-account")
            host.reply.completeExceptionally(ApiException(401, "old response")); request.join()
            assertEquals(0, host.invalidations)
            assertEquals("new-account", host.user?.text("id"))
            assertTrue(notifications.notifications.isEmpty()); assertFalse(notifications.notificationsLoading)
        }
    }

    @Test fun currentUnauthorizedInboxFailureStillRejectsTheSession() = runTest {
        val host = Host("/api/bonds/notifications")
        val notifications = PortfolioNotifications(this, TimeSource.Monotonic, host)
        val request = launch { notifications.fetchNotifications() }; host.entered.await()
        host.reply.completeExceptionally(ApiException(401, "current response")); request.join()
        assertEquals(1, host.invalidations); assertNull(host.user); assertFalse(notifications.notificationsLoading)
    }

    @Test fun cancelledNonCooperativeInboxReadDoesNotPublishRowsOrErrors() = runTest {
        val host = Host("/api/bonds/notifications").apply { ignoresCancellation = true }
        val notifications = PortfolioNotifications(this, TimeSource.Monotonic, host)
        val request = launch { notifications.fetchNotifications() }; host.entered.await()
        request.cancel()
        host.reply.complete(JsonRecord().put("notifications", JsonList(listOf(JsonRecord().put("id", "late")))).put("unreadCount", 1))
        request.join()
        assertTrue(request.isCancelled); assertTrue(notifications.notifications.isEmpty())
        assertNull(notifications.notificationsError); assertEquals(0, notifications.unreadNotifications)
    }
}
