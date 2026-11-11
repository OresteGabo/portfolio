package dev.orestegabo.portfolio

import android.content.Intent
import android.content.pm.ShortcutManager
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import dev.orestegabo.portfolio.investments.NativeAppActions
import org.junit.Assert.*
import org.junit.Test

class AppIconShortcutTest {
    @Test fun installedShortcutsResolveAndNavigationIsDispatchedOnce() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val shortcuts=context.getSystemService(ShortcutManager::class.java).manifestShortcuts
        assertEquals(setOf("import-pdf","add-purchase","calendar"),shortcuts.map { it.id }.toSet())
        for(shortcut in shortcuts) assertNotNull(context.packageManager.resolveActivity(shortcut.intent!!,0))
        instrumentation.runOnMainSync {
            try {
                NativeAppActions.clear()
                val intent=Intent(Intent.ACTION_VIEW,Uri.parse("structcapital://action/calendar"))
                receiveAppAction(intent)
                assertEquals("calendar",NativeAppActions.take())
                receiveAppAction(intent)
                assertNull(NativeAppActions.pending)
                receiveAppAction(Intent(Intent.ACTION_VIEW,Uri.parse("structcapital://action/delete-account")))
                assertNull(NativeAppActions.pending)
            } finally { NativeAppActions.clear() }
        }
    }
}
