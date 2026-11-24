package dev.ohs.demo.anc

class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

@androidx.compose.runtime.Composable
actual fun BrowserBackHandler(enabled: Boolean, onBack: () -> Unit) {}
