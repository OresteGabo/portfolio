package dev.orestegabo.portfolio

import android.app.NotificationManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit operator check only: the external sender targets this disposable emulator's token. */
class FirebaseDeliverySetupTest {
    @Test fun receivesARealFirebaseMessageWithoutAnAccountSession() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("firebaseSetupCheck") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue("This APK must include its Firebase configuration", FirebaseApp.getApps(context).isNotEmpty())
        val entry = context.getSharedPreferences("connection",0)
        val oldChoice = entry.getString("sessionChoice",null)
        val push = context.getSharedPreferences("portfolio.push",0)
        val oldToken = push.getString("token",null)
        val file = File(context.cacheDir,"firebase-setup-token.txt")
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            // No portfolio login, production device registration or private account data is used.
            entry.edit().putString("sessionChoice","manual-account").commit()
            if(Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.executeShellCommand(
                "pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS").close()
            val ready = CountDownLatch(1)
            val failure = java.util.concurrent.atomic.AtomicReference("")
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                file.writeText(token); ready.countDown()
            }.addOnFailureListener { error ->
                failure.set((generateSequence(error as Throwable) { it.cause }.take(6).joinToString(" → ") { it.javaClass.simpleName + ": " + it.message.orEmpty() })
                    .replace(Regex("AIza[A-Za-z0-9_-]+"), "[redacted]").take(600))
                ready.countDown()
            }
            assertTrue("Firebase token request timed out",ready.await(45,TimeUnit.SECONDS))
            assertTrue("Firebase token request failed: ${failure.get()}",file.exists() && file.length()>16)
            val deadline = System.currentTimeMillis()+120_000
            while(System.currentTimeMillis()<deadline) {
                if(manager.activeNotifications.any { it.notification.extras.getCharSequence("android.title").toString()=="Struct Capital delivery check" }) return
                Thread.sleep(100)
            }
            fail("The external Firebase test message was not received")
        } finally {
            manager.cancelAll()
            file.delete()
            entry.edit().apply { if(oldChoice==null)remove("sessionChoice") else putString("sessionChoice",oldChoice) }.commit()
            push.edit().apply { if(oldToken==null)remove("token") else putString("token",oldToken) }.commit()
            FirebaseMessaging.getInstance().deleteToken()
        }
    }
}
