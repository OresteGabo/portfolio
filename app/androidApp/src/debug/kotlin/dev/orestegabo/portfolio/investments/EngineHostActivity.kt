package dev.orestegabo.portfolio.investments

import android.app.Activity
import android.os.Bundle

/** Debug-only window used to exercise the local engine with a real Android view lifecycle. */
class EngineHostActivity : Activity() {
    lateinit var engine: LocalInvestmentEngine
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LocalInvestmentEngine(this)
        setContentView(engine.view)
    }
    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}
