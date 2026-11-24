package dev.ohs.demo.anc

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

@Composable
actual fun BrowserBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val currentOnBack by rememberUpdatedState(onBack)
    DisposableEffect(enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        var popped = false
        pushHistoryEntry()
        val listener = addPopStateListener { popped = true; currentOnBack() }
        onDispose {
            removePopStateListener(listener)
            // Left with the in-app arrow or button: drop the entry, so Back doesn't need two presses.
            if (!popped) historyBack()
        }
    }
}

private fun pushHistoryEntry(): Unit = js("history.pushState(null, '')")

private fun historyBack(): Unit = js("history.back()")

private fun addPopStateListener(onPop: () -> Unit): JsAny =
    js("{ const listener = () => onPop(); window.addEventListener('popstate', listener); return listener; }")

private fun removePopStateListener(listener: JsAny): Unit = js("window.removeEventListener('popstate', listener)")
