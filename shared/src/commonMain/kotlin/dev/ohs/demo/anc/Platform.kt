package dev.ohs.demo.anc

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

/**
 * On the web, adds a browser history entry while [enabled], so the browser's Back button calls
 * [onBack] instead of leaving the app. Other platforms have a system Back (BackHandler) instead.
 */
@androidx.compose.runtime.Composable
expect fun BrowserBackHandler(enabled: Boolean, onBack: () -> Unit)
