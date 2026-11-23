package dev.ohs.demo.anc

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Antenatal Care",
        state = rememberWindowState(size = DpSize(1200.dp, 800.dp)),
    ) {
        // Narrower than a phone, the layout would squeeze.
        window.minimumSize = Dimension(360, 560)
        App()
    }
}
