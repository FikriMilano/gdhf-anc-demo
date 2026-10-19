package dev.ohs.demo.anc

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "gdhf-anc-demo") {
        App()
    }
}
