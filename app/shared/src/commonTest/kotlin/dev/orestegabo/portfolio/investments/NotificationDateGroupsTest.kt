package dev.orestegabo.portfolio.investments

import kotlinx.datetime.LocalDate
import kotlin.test.*

class NotificationDateGroupsTest {
    private fun event(id: String,date: String,kind: String="prices")=JsonRecord().put("id",id).put("date",date).put("kind",kind)

    @Test fun groupsNewestFirstWithoutChangingRecordsOrEqualDateOrder() {
        val old=event("old","2026-11-23")
        val today=event("today","2026-11-26","security").put("dueDate","2027-01-01")
        val yesterday=event("yesterday","2026-11-25")
        val newerOld=event("newer-old","2026-11-24")
        val sameDay=event("same-day","2026-11-26","coupons")
        val input=listOf(old,today,yesterday,newerOld,sameDay)
        val before=input.map { it.toString() }
        val groups=notificationDateGroups(input,LocalDate(2026,11,26))
        assertEquals(listOf(NotificationDateGroup.TODAY,NotificationDateGroup.YESTERDAY,NotificationDateGroup.OLDER),groups.keys.toList())
        assertEquals(listOf(today,sameDay),groups[NotificationDateGroup.TODAY])
        assertEquals(listOf(yesterday),groups[NotificationDateGroup.YESTERDAY])
        assertEquals(listOf(newerOld,old),groups[NotificationDateGroup.OLDER])
        assertEquals(before,input.map { it.toString() })
        assertSame(today,groups.getValue(NotificationDateGroup.TODAY).first())
    }

    @Test fun yearAndLeapDayBoundariesUseTheProvidedMarketDay() {
        assertEquals(NotificationDateGroup.YESTERDAY,notificationDateGroups(listOf(event("dec","2025-12-31")),LocalDate(2026,1,1)).keys.single())
        assertEquals(NotificationDateGroup.YESTERDAY,notificationDateGroups(listOf(event("leap","2024-02-29")),LocalDate(2024,3,1)).keys.single())
    }

    @Test fun onlyNonemptySectionsAppearAndEarlierPagesShareOneOlderGroup() {
        assertTrue(notificationDateGroups(emptyList(),LocalDate(2026,11,26)).isEmpty())
        val first=listOf(event("one","2026-11-24"))
        val more=listOf(event("two","2026-11-20"),event("three","2026-11-19"))
        val groups=notificationDateGroups(first+more,LocalDate(2026,11,26))
        assertEquals(listOf(NotificationDateGroup.OLDER),groups.keys.toList())
        assertEquals(first+more,groups.values.single())
    }

    @Test fun unknownOrFutureDatesAreRetainedWithoutBeingMislabelledAsPastEvents() {
        val records=listOf(event("missing",""),event("future","2026-11-27"),event("invalid","2026-99-99"))
        val groups=notificationDateGroups(records,LocalDate(2026,11,26))
        assertEquals(listOf(NotificationDateGroup.OTHER),groups.keys.toList())
        assertEquals(setOf("missing","future","invalid"),groups.values.single().map { it.text("id") }.toSet())
    }
}
