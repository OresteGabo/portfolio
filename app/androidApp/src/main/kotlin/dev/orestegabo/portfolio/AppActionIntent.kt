package dev.orestegabo.portfolio

import android.content.Intent
import dev.orestegabo.portfolio.investments.NativeAppActions

internal fun receiveAppAction(intent: Intent) {
    if (intent.getBooleanExtra("struct.action.dispatched",false)) return
    val uri = intent.data
    val action = intent.getStringExtra("appAction")
        ?: if (intent.action == Intent.ACTION_VIEW && uri?.scheme == "structcapital" && uri.host == "action") uri.lastPathSegment else null
    if (action != null && NativeAppActions.request(action)) intent.putExtra("struct.action.dispatched",true)
}
