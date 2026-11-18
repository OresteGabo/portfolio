package dev.orestegabo.portfolio.investments

import android.content.Context

/** Native local extraction and shared Kotlin rules; no browser, JS bridge or remote OCR. */
class LocalInvestmentEngine(context: Context) : KotlinInvestmentEngine(AndroidPdfTextExtractor(context.applicationContext)::extract)
