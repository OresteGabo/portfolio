package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

// Platform integration effects are separate from screen ordering and navigation state.

@Composable
internal fun PublishHomeWidget(model: PortfolioModel) {
    // Each native host persists only a safe projection; widgets need no signed-in API session.
    LaunchedEffect(model.user, model.portfolioReady, model.calendar, model.portfolio, model.documents, model.couponReceipts, model.bondReminderMonths, AppLanguage.code) {
        if (HomeWidgetBridge.available && !model.isGuest && model.user != null && model.portfolioReady)
            HomeWidgetBridge.publish(homeWidgetSnapshot(model))
    }
}

@Composable
internal fun HomeWidgetRouteEffects(model: PortfolioModel, openCalendarAt: (kotlinx.datetime.LocalDate?) -> Unit,
    showPrivacyHint: () -> Unit = {}) {
    LaunchedEffect(HomeWidgetBridge.routeRevision, model.user, model.locked) {
        if (model.user != null && !model.locked) {
            HomeWidgetBridge.consumeCalendarRequest(HomeWidgetBridge.routeRevision)?.let { date ->
                openCalendarAt(calendarDate(date))
                showPrivacyHint()
            }
        }
    }
}

@Composable
internal fun AccountPlatformEffects(model: PortfolioModel) {
    LaunchedEffect(model.user, model.locked) {
        if (model.user != null && !model.locked) {
            withFrameNanos { }
            model.dashboardPresented()
            NativePushBridge.accountReady()
        }
    }
    LaunchedEffect(model.user, model.locked, NativePushBridge.registration, NativePushBridge.allowed, NativePushBridge.liveStartToken, NativePushBridge.liveActivities, AppLanguage.code) {
        model.syncPushDevice()
    }
}

@Composable
internal fun NotificationRouteEffects(model: PortfolioModel, tab: String, openNotificationInbox: () -> Unit) {
    var handledInboxRequest by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(NativePushBridge.inboxRequest, model.user, model.locked) {
        if(NativePushBridge.inboxRequest>handledInboxRequest && model.user!=null && !model.locked) {
            openNotificationInbox();handledInboxRequest=NativePushBridge.inboxRequest
        }
    }
    LaunchedEffect(NativePushBridge.updateRevision) { if(model.user!=null && !model.locked)model.refreshNotifications() }
    LaunchedEffect(tab,model.user,model.locked) { if(tab=="Notifications" && model.user!=null && !model.locked)model.refreshNotifications() }
}

@Composable
internal fun NoticeLifetimeEffects(model: PortfolioModel) {
    LaunchedEffect(model, model.messageRevision) {
        val revision = model.messageRevision
        if (model.message != null) {
            kotlinx.coroutines.delay(3_000)
            if (model.messageRevision == revision) model.message = null
        }
    }

}

@Composable
internal fun rememberInvestmentAppEngine(model: PortfolioModel): InvestmentEngine {
    val engine = LocalImportEngine.current ?: if (model.isGuest && model.guestPath == GuestPath.PERSONAL) rememberGuestInvestmentEngine() else if (model.isGuest) remember { GuestUnavailableEngine() } else rememberInvestmentEngine()
    DisposableEffect(engine) { onDispose { engine.destroy() } }
    return engine
}
