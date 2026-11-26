@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.PortfolioDataApi
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*
import kotlin.time.TimeSource

class MarkAllNotificationsReadTest {
    private class Host : PortfolioNotificationHost {
        var writes=0;var fetches=0
        var bulk: suspend () -> JsonRecord = { acknowledgement() }
        var single: suspend () -> JsonRecord = { JsonRecord().put("saved",true) }
        override var user: JsonRecord?=JsonRecord().put("id","synthetic-account")
        override var locked=false
        override var isGuest=false
        override val monthlyInvestmentTarget=100_000L
        override var error: String?=null
        override fun applyMonthlyTarget(amount: Long) {}
        override suspend fun invalidateRejectedSession(failure: Exception)=false
        override val api=object : PortfolioDataApi {
            override suspend fun request(path: String,method: String,body: JsonRecord?): JsonRecord=when {
                path.endsWith("/read-all") -> { assertEquals("POST",method);writes++;bulk() }
                path.endsWith("/read") -> single()
                path.endsWith("/preferences") -> JsonRecord().put("configured",true).put("categories",JsonRecord())
                path.endsWith("/coupons") -> JsonRecord().put("receipts",JsonList())
                path.endsWith("/notifications") -> {
                    fetches++
                    JsonRecord().put("unreadCount",25).put("nextBefore","2026-11-01").put("notifications",JsonList(listOf(
                        JsonRecord().put("id","unread").put("date","2026-11-25").put("read",false),
                        JsonRecord().put("id","read").put("date","2026-11-24").put("read",true))))
                }
                else -> error("Unexpected synthetic request: $path")
            }
            override suspend fun bytes(path: String,method: String,body: JsonRecord?): ByteArray=error("No files")
        }
    }
    companion object { private fun acknowledgement(remaining: Int=0)=JsonRecord().put("saved",true).put("unreadCount",remaining) }

    @Test fun waitsForAcknowledgementCoalescesTapsAndKeepsLoadedPages() = runTest {
        val host=Host();val center=PortfolioNotifications(this,TimeSource.Monotonic,host)
        center.fetchNotifications()
        val gate=CompletableDeferred<JsonRecord>();host.bulk={ gate.await() }
        center.markAllAsRead();center.markAllAsRead();center.refreshNotifications();center.moreNotifications();runCurrent()
        assertTrue(center.markingAllRead);assertEquals(1,host.writes);assertEquals(1,host.fetches)
        assertEquals(25,center.unreadNotifications);assertFalse(center.notifications.first().optBoolean("read"))
        gate.complete(acknowledgement());advanceUntilIdle()
        assertFalse(center.markingAllRead);assertEquals(0,center.unreadNotifications)
        assertTrue(center.notifications.all { it.optBoolean("read") });assertEquals("2026-11-01",center.notificationNextBefore)
        assertEquals(listOf("unread","read"),center.notifications.map { it.text("id") })
        center.markAllAsRead();advanceUntilIdle();assertEquals(1,host.writes)
    }

    @Test fun failureOrMalformedAcknowledgementPreservesUnreadStateAndAllowsRetry() = runTest {
        for(reply in listOf<suspend () -> JsonRecord>({ error("Offline") },{ JsonRecord() })) {
            val host=Host();val center=PortfolioNotifications(this,TimeSource.Monotonic,host);center.fetchNotifications()
            host.bulk=reply;center.markAllAsRead();advanceUntilIdle()
            assertEquals(25,center.unreadNotifications);assertFalse(center.notifications.first().optBoolean("read"))
            assertNotNull(center.notificationsError);assertFalse(center.markingAllRead)
            host.bulk={ acknowledgement(1) };center.markAllAsRead();advanceUntilIdle()
            assertEquals(1,center.unreadNotifications);assertNull(center.notificationsError)
        }
    }

    @Test fun oldAccountReplyCannotMarkNewAccountNotificationsRead() = runTest {
        val host=Host();val center=PortfolioNotifications(this,TimeSource.Monotonic,host);center.fetchNotifications()
        val gate=CompletableDeferred<JsonRecord>();host.bulk={ gate.await() }
        center.markAllAsRead();runCurrent();center.clear();host.user=JsonRecord().put("id","new-account")
        center.fetchNotifications();gate.complete(acknowledgement());advanceUntilIdle()
        assertEquals(25,center.unreadNotifications);assertFalse(center.notifications.first().optBoolean("read"))
        assertFalse(center.markingAllRead);assertNull(center.notificationsError)
    }

    @Test fun cancellationDoesNotReportSuccessOrError() = runTest {
        val host=Host();val job=Job();val center=PortfolioNotifications(CoroutineScope(coroutineContext+job),TimeSource.Monotonic,host)
        center.fetchNotifications();host.bulk={ awaitCancellation() };center.markAllAsRead();runCurrent()
        job.cancel();runCurrent()
        assertFalse(center.markingAllRead);assertEquals(25,center.unreadNotifications)
        assertFalse(center.notifications.first().optBoolean("read"));assertNull(center.notificationsError)
    }

    @Test fun staleSingleReadFailureDoesNotOverrideBulkSuccess() = runTest {
        val host=Host();val center=PortfolioNotifications(this,TimeSource.Monotonic,host);center.fetchNotifications()
        val gate=CompletableDeferred<JsonRecord>();host.single={ gate.await() }
        center.readNotification("unread");runCurrent();center.markAllAsRead();runCurrent()
        gate.completeExceptionally(IllegalStateException("Old read failed"));advanceUntilIdle()
        assertNull(center.notificationsError);assertEquals(0,center.unreadNotifications)
    }

    @Test fun unavailableSessionsNeverStartBulkWrites() = runTest {
        for(state in listOf("guest","locked","signed-out")) {
            val host=Host();val center=PortfolioNotifications(this,TimeSource.Monotonic,host);center.fetchNotifications()
            when(state) { "guest" -> host.isGuest=true; "locked" -> host.locked=true; else -> host.user=null }
            center.markAllAsRead();advanceUntilIdle();assertEquals(0,host.writes)
        }
    }
}
