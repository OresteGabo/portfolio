package dev.orestegabo.portfolio.investments

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in on disposable emulator userdata only. No financial data or credentials are written. */
class GuestStartupFixtureTest {
    @Test fun prepareEmptyOfflineGuestForProcessLaunchMeasurements() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("isolatedGuestBenchmark")=="true")
        check(Build.HARDWARE in listOf("ranchu","goldfish") || Build.FINGERPRINT.contains("emulator"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(!java.io.File(context.noBackupFilesDir,"guest-portfolios/personal.scg").exists())
        check(!java.io.File(context.noBackupFilesDir,"guest-portfolios/personal.scg.bak").exists())
        val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("connection",0)
        check(prefs.getString("sessionChoice",null)==null) { "Use fresh disposable userdata, not an existing workspace" }
        check(prefs.edit().putString("sessionChoice","guest-personal-v1").putBoolean("onboardingCompleted",true)
            .putBoolean("screenCaptureAllowed",true).putString("language","en").commit())
    }
}
