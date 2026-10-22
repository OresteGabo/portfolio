package dev.orestegabo.portfolio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform