package dev.orestegabo.portfolio.investments

import dev.orestegabo.portfolio.network.ApiException
import dev.orestegabo.portfolio.network.PortfolioDataApi

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Narrow account boundary: this feature never owns authentication or replaces the session. */
internal interface PortfolioNotificationHost {
    val api: PortfolioDataApi
    val user: JsonRecord?
    val locked: Boolean
    val isGuest: Boolean
    val monthlyInvestmentTarget: Long
    val investmentGoal: InvestmentGoal get() = InvestmentGoal(monthlyAmount = monthlyInvestmentTarget)
    fun applyInvestmentGoal(goal: InvestmentGoal) = applyMonthlyTarget(goal.monthlyAmount)
    var error: String?
    fun applyMonthlyTarget(amount: Long)
    suspend fun invalidateRejectedSession(failure: Exception): Boolean
}

/** Owns notification settings, inbox pagination, reminder choices and push registration. */
internal class PortfolioNotifications(
    private val scope: CoroutineScope,
    private val clock: TimeSource,
    private val host: PortfolioNotificationHost,
) {
    private val api get() = host.api
    private val user get() = host.user
    private val locked get() = host.locked
    private val isGuest get() = host.isGuest
    private val monthlyInvestmentTarget get() = host.monthlyInvestmentTarget
    private var error: String?
        get() = host.error
        set(value) { host.error = value }

    var bondReminderFeedback by mutableStateOf<String?>(null)
        private set
    fun dismissBondReminderFeedback() { bondReminderFeedback = null }
    private var bondReminderRevision = 0
    var bondReminderMonths by mutableStateOf<Set<String>>(emptySet())
        private set
    var bondRemindersLoaded by mutableStateOf(false)
        private set
    var bondRemindersLoading by mutableStateOf(false)
        private set
    var bondReminderSaving by mutableStateOf<String?>(null)
        private set
    var bondRemindersError by mutableStateOf<String?>(null)
        private set
    fun loadBondReminders() {
        if (isGuest || user == null || locked || bondRemindersLoading || bondReminderSaving != null) return
        val revision = notificationGeneration
        bondRemindersLoading = true
        val readRevision = ++bondReminderRevision
        scope.launch {
            try {
                val result = api.request("/api/bonds/notifications/bond-reminders")
                if (revision == notificationGeneration && readRevision == bondReminderRevision) {
                    require(result.has("months"))
                    bondReminderMonths = result.optJSONArray("months")?.let { values ->
                        (0 until values.length()).map { values.getString(it) }.toSet()
                    }.orEmpty()
                    bondRemindersLoaded = true; bondRemindersError = null
                    NativePushBridge.reconcileReminders(bondReminderMonths.joinToString(","))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == notificationGeneration && readRevision == bondReminderRevision) { bondRemindersError = "Bond reminders could not be loaded. Try again."; bondReminderFeedback = bondRemindersError } }
            finally { if (revision == notificationGeneration) bondRemindersLoading = false }
        }
    }
    fun setBondMonthReminder(month: String, enabled: Boolean) {
        if (user == null || locked || !bondRemindersLoaded || bondReminderSaving != null || !validBondReminderMonth(month)) return
        val revision = notificationGeneration
        bondReminderSaving = month
        ++bondReminderRevision // An earlier refresh must not overwrite this acknowledged choice.
        scope.launch {
            try {
                val result = api.request("/api/bonds/notifications/bond-reminders/$month", "PUT", JsonRecord().put("enabled", enabled))
                require(result.has("months"))
                if (revision == notificationGeneration) {
                    val values = result.optJSONArray("months")
                    bondReminderMonths = if(values == null) emptySet() else (0 until values.length()).map { values.getString(it) }.toSet()
                    bondRemindersError = null
                    NativePushBridge.reconcileReminders(bondReminderMonths.joinToString(","))
                    if(enabled && NativePushBridge.available && !NativePushBridge.allowed) NativePushBridge.requestPermission()
                    bondReminderFeedback = when {
                        !enabled -> "Reminder removed."
                        !NativePushBridge.available -> "Reminder saved. Phone alerts need setup."
                        !NativePushBridge.allowed -> "Reminder saved. Enable phone notifications."
                        else -> "Reminder saved. We’ll alert you when the form is ready."
                    }
                    if(enabled) fetchNotificationSettings()
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == notificationGeneration) { bondRemindersError = "Bond reminder could not be saved. Try again."; bondReminderFeedback = bondRemindersError } }
            finally { if (revision == notificationGeneration) bondReminderSaving = null }
        }
    }
    var notificationPreferences by mutableStateOf(JsonRecord())
    var notificationSettingsAvailable by mutableStateOf(false)
        private set
    private var pendingNotificationPreferences by mutableStateOf<Map<String, Boolean>>(emptyMap())
    val notificationSettingsSaving get() = pendingNotificationPreferences.isNotEmpty()
    private val notificationSettingsMutex = Mutex()
    private var goalRevision = 0
    fun notificationPreferenceSaving(category: String? = null) = pendingNotificationPreferences.containsKey(category ?: "autoCoupon")
    fun notificationPreferenceEnabled(category: String? = null): Boolean = pendingNotificationPreferences[category ?: "autoCoupon"]
        ?: if (category == null) notificationPreferences.optBoolean("autoCoupon")
        else notificationPreferences.optJSONObject("categories")?.optBoolean(category, true) ?: true
    var notificationDate by mutableStateOf(Today)
        private set
    var couponReceipts by mutableStateOf<List<JsonRecord>>(emptyList())
    var couponTrackingAvailable by mutableStateOf(false)
    suspend fun fetchNotificationSettings() = notificationSettingsMutex.withLock {
        val revision=notificationGeneration;val account=user?.text("id")
        val goalReadRevision = goalRevision
        try {
            var value=api.request("/api/bonds/notifications/preferences")
            currentCoroutineContext().ensureActive()
            if(revision!=notificationGeneration || account!=user?.text("id") || !value.has("categories"))return@withLock
            if(!value.optBoolean("configured")) value=api.request("/api/bonds/notifications/preferences","PUT",host.investmentGoal.toRecord())
            currentCoroutineContext().ensureActive()
            if(revision!=notificationGeneration || account!=user?.text("id"))return@withLock
            notificationPreferences=value;notificationSettingsAvailable=true
            if (goalReadRevision == goalRevision) host.applyInvestmentGoal(investmentGoalFromRecord(value, host.investmentGoal))
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) {
            currentCoroutineContext().ensureActive()
            if (revision == notificationGeneration && account == user?.text("id")) invalidateRejectedSession(failure)
        }
    }
    fun updateInvestmentGoal(goal: InvestmentGoal) {
        val revision = notificationGeneration
        val account = user?.text("id")
        val saveRevision = ++goalRevision
        scope.launch {
            try {
                notificationSettingsMutex.withLock {
                    if (revision != notificationGeneration || account != user?.text("id") || locked) return@withLock
                    val saved = api.request("/api/bonds/notifications/preferences", "PUT", goal.toRecord())
                    if (goal.period == InvestmentGoalPeriod.YEARLY) check(saved.text("goalPeriod") == "yearly") {
                        "Yearly reminder settings need a server update."
                    }
                    if (revision == notificationGeneration && account == user?.text("id") && saveRevision == goalRevision) {
                        val acknowledged = investmentGoalFromRecord(saved, goal)
                        host.applyInvestmentGoal(acknowledged)
                        val next = JsonRecord(notificationPreferences.toString())
                        next.put("monthlyTarget", acknowledged.monthlyAmount).put("yearlyTarget", acknowledged.yearlyAmount).put("goalPeriod", acknowledged.period.key)
                        notificationPreferences = next
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == notificationGeneration && saveRevision == goalRevision) error = "Goal saved on this device. Reminder sync failed. Try saving again." }
        }
    }
    fun updateNotificationPreference(category: String? = null, enabled: Boolean) {
        if (!notificationSettingsAvailable || notificationPreferenceSaving(category) || user == null || locked) return
        val revision = notificationGeneration
        val account = user?.text("id")
        val key = category ?: "autoCoupon"
        // Pending values are presentation-only; unrelated switches retain their acknowledged values.
        pendingNotificationPreferences = pendingNotificationPreferences + (key to enabled)
        scope.launch {
            var updated = false
            try {
                notificationSettingsMutex.withLock {
                    if (revision != notificationGeneration || account != user?.text("id") || locked) return@withLock
                    val change = JsonRecord()
                    if (category == null) change.put("autoCoupon", enabled)
                    else {
                        // Build inside the serialized write, after preceding changes were acknowledged.
                        val categories = JsonRecord(notificationPreferences.optJSONObject("categories")?.toString() ?: "{}")
                        categories.put(category, enabled); change.put("categories", categories)
                    }
                    val saved = api.request("/api/bonds/notifications/preferences", "PUT", change)
                    if (revision != notificationGeneration || account != user?.text("id")) return@withLock
                    val next = JsonRecord(notificationPreferences.toString())
                    if (category == null) {
                        check(saved.has("autoCoupon") && saved.optBoolean("autoCoupon", !enabled) == enabled)
                        next.put("autoCoupon", enabled)
                    } else {
                        val acknowledged = saved.optJSONObject("categories")
                        check(acknowledged?.has(category) == true && acknowledged.optBoolean(category, !enabled) == enabled)
                        val categories = JsonRecord(notificationPreferences.optJSONObject("categories")?.toString() ?: "{}")
                        categories.put(category, enabled); next.put("categories", categories)
                    }
                    notificationPreferences = next
                    updated = true
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { if (revision == notificationGeneration) error = "Notification settings could not be saved." }
            finally {
                if (revision == notificationGeneration) pendingNotificationPreferences = pendingNotificationPreferences - key
            }
            if (updated && revision == notificationGeneration && account == user?.text("id")) fetchNotifications()
        }
    }
    var notifications by mutableStateOf<List<JsonRecord>>(emptyList())
        private set
    var unreadNotifications by mutableIntStateOf(0)
        private set
    var notificationNextBefore by mutableStateOf<String?>(null)
        private set
    var notificationsLoading by mutableStateOf(false)
        private set
    var markingAllRead by mutableStateOf(false)
        private set
    private var inboxReadRevision = 0
    var notificationsError by mutableStateOf<String?>(null)
        private set
    var notificationLoadedAt: TimeMark? = null; private set
    var notificationGeneration = 0; private set
    suspend fun fetchNotifications(more: Boolean = false) {
        if(user==null || notificationsLoading || markingAllRead)return
        val account=user?.text("id");val revision=notificationGeneration
        notificationsLoading=true
        try {
            if(!more)fetchNotificationSettings()
            currentCoroutineContext().ensureActive()
            if (revision != notificationGeneration || account != user?.text("id") || user == null || locked) return
            val response=api.request("/api/bonds/notifications"+(if(more) "?before=${notificationNextBefore ?: return}" else ""))
            currentCoroutineContext().ensureActive()
            if(revision!=notificationGeneration || account!=user?.text("id"))return
            notificationDate=calendarDate(response.text("serverDate")) ?: Today
            try {
                val receipts=api.request("/api/bonds/notifications/coupons")
                currentCoroutineContext().ensureActive()
                if(revision==notificationGeneration && receipts.has("receipts")) { couponReceipts=receipts.rows("receipts");couponTrackingAvailable=true }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { }
            currentCoroutineContext().ensureActive()
            if(revision!=notificationGeneration || account!=user?.text("id"))return
            val rows=response.rows("notifications")
            notifications=if(more)(notifications+rows).distinctBy { it.text("id") } else rows
            unreadNotifications=response.optInt("unreadCount")
            notificationNextBefore=response.text("nextBefore").takeIf { it.isNotBlank() && it!="null" }
            notificationsError=null;notificationLoadedAt=clock.markNow()
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(failure: Exception) {
            currentCoroutineContext().ensureActive()
            if (revision != notificationGeneration || account != user?.text("id")) return
            if (invalidateRejectedSession(failure)) return
            if(revision==notificationGeneration) notificationsError=if(failure is ApiException && failure.status==404)
                "Notifications need the updated server." else "Notifications could not be loaded. Try again."
        } finally { if(revision==notificationGeneration)notificationsLoading=false }
    }
    private suspend fun invalidateRejectedSession(failure: Exception) = host.invalidateRejectedSession(failure)
    fun refreshNotifications() { if(user!=null && !locked)scope.launch { fetchNotifications() } }
    fun moreNotifications() { if(user!=null && !locked)scope.launch { fetchNotifications(true) } }
    fun readNotification(id: String) {
        if(markingAllRead)return
        val revision=notificationGeneration;val readRevision=inboxReadRevision
        scope.launch {
            try {
                api.request("/api/bonds/notifications/read","POST",JsonRecord().put("ids",JsonList(listOf(id))))
                if(revision==notificationGeneration && readRevision==inboxReadRevision) {
                    val wasUnread=notifications.any { it.text("id")==id && !it.optBoolean("read") }
                    notifications=notifications.map { if(it.text("id")==id)JsonRecord(it.toString()).put("read",true) else it }
                    if(wasUnread)unreadNotifications=(unreadNotifications-1).coerceAtLeast(0)
                    notificationsError=null
                }
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(_: Exception) { if(revision==notificationGeneration && readRevision==inboxReadRevision)notificationsError="Could not mark this notification as read." }
        }
    }

    fun markAllAsRead() {
        if(user==null || locked || isGuest || markingAllRead || notificationsLoading || unreadNotifications==0)return
        val revision=notificationGeneration;val account=user?.text("id")
        markingAllRead=true;inboxReadRevision++
        scope.launch {
            try {
                val response=api.request("/api/bonds/notifications/read-all","POST",JsonRecord())
                currentCoroutineContext().ensureActive()
                if(revision!=notificationGeneration || account!=user?.text("id"))return@launch
                val remaining=response.optInt("unreadCount",-1)
                check(response.optBoolean("saved") && remaining>=0)
                notifications=notifications.map { JsonRecord(it.toString()).put("read",true) }
                unreadNotifications=remaining
                notificationsError=null
            } catch(cancelled: CancellationException) { throw cancelled }
            catch(failure: Exception) {
                currentCoroutineContext().ensureActive()
                if(revision!=notificationGeneration || account!=user?.text("id"))return@launch
                if(invalidateRejectedSession(failure))return@launch
                notificationsError="Could not mark all notifications as read. Try again."
            } finally { if(revision==notificationGeneration)markingAllRead=false }
        }
    }

    suspend fun signInActivity(notificationId: String?): JsonRecord {
        require(notificationId == null || notificationId.matches(Regex("[A-Za-z0-9_-]{1,120}"))) { "Invalid security notification." }
        return api.request("/api/bonds/notifications/sign-in-activity" + (notificationId?.let { "?notificationId=$it" } ?: ""))
    }

    suspend fun syncPushDevice() {
        if(user==null || locked)return
        if(!bondRemindersLoaded) loadBondReminders()
        try {
            if(NativePushBridge.deviceId.isNotBlank())api.request("/api/bonds/notifications/session-device","POST",JsonRecord().withDeviceIdentity())
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { }
        val registration=NativePushBridge.registration ?: return
        try {
            if(NativePushBridge.allowed) api.request("/api/bonds/notifications/devices","POST",JsonRecord(registration.toString()).put("language",AppLanguage.code).put("liveStartToken",NativePushBridge.liveStartToken))
            else api.request("/api/bonds/notifications/devices/${registration.text("deviceId")}","DELETE")
            if(NativePushBridge.allowed) for (activity in NativePushBridge.liveActivities) {
                api.request("/api/bonds/notifications/bond-live-activities", "POST", JsonRecord(activity.toString()).put("deviceId",registration.text("deviceId")))
            }
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { /* A provider or older API must not block the portfolio. */ }
    }
    /** Advance the revision so in-flight reads/writes cannot repopulate a cleared session. */
    fun clear() {
        bondReminderMonths = emptySet(); bondRemindersLoaded = false; bondRemindersLoading = false
        bondReminderSaving = null; bondRemindersError = null; bondReminderFeedback = null
        notificationPreferences = JsonRecord(); notificationSettingsAvailable = false
        pendingNotificationPreferences = emptyMap()
        couponReceipts = emptyList(); couponTrackingAvailable = false; notificationDate = Today
        notificationGeneration++
        notifications = emptyList(); unreadNotifications = 0; notificationNextBefore = null
        notificationsError = null; notificationsLoading = false; markingAllRead = false; inboxReadRevision++; notificationLoadedAt = null
    }
}
