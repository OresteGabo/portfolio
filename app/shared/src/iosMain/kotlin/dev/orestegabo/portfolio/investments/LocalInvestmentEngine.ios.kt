package dev.orestegabo.portfolio.investments

/** System PDFKit/Vision extraction, shared Kotlin document rules and simulator. */
class IosInvestmentEngine : KotlinInvestmentEngine(IosPdfTextExtractor()::extract)
