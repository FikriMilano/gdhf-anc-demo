package dev.ohs.demo.anc

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

@androidx.compose.runtime.Composable
actual fun BrowserBackHandler(enabled: Boolean, onBack: () -> Unit) {}
