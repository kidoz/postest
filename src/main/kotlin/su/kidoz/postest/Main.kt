package su.kidoz.postest

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import su.kidoz.postest.di.appModules
import java.awt.Desktop
import java.awt.Taskbar

private const val APP_NAME = "Postest"

fun main() {
    // Set macOS application name before any AWT/Swing initialization
    System.setProperty("apple.awt.application.name", APP_NAME)
    System.setProperty("apple.laf.useScreenMenuBar", "true")

    // Set up macOS About handler
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
            desktop.setAboutHandler {
                javax.swing.JOptionPane.showMessageDialog(
                    null,
                    """
                    $APP_NAME
                    Version 1.0.0

                    A modern REST API client for testing and debugging HTTP APIs.

                    Developed with Kotlin and Compose Multiplatform.

                    © 2025 kidoz. All rights reserved.
                    """.trimIndent(),
                    "About $APP_NAME",
                    javax.swing.JOptionPane.INFORMATION_MESSAGE,
                )
            }
        }
    }

    // Set dock icon on macOS
    if (Taskbar.isTaskbarSupported()) {
        val taskbar = Taskbar.getTaskbar()
        if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
            try {
                val iconUrl = object {}.javaClass.getResource("/icon.png")
                if (iconUrl != null) {
                    val icon = javax.imageio.ImageIO.read(iconUrl)
                    taskbar.iconImage = icon
                }
            } catch (e: Exception) {
                // Ignore icon loading errors
            }
        }
    }

    // Initialize Koin DI
    startKoin {
        modules(appModules)
    }

    application {
        val windowState =
            rememberWindowState(
                size = DpSize(1400.dp, 900.dp),
                position = WindowPosition(Alignment.Center),
            )

        val iconBytes =
            remember {
                Thread
                    .currentThread()
                    .contextClassLoader
                    .getResourceAsStream("icon.png")
                    ?.use { it.readBytes() }
            }
        val iconPainter =
            remember(iconBytes) {
                iconBytes?.let {
                    BitmapPainter(Image.makeFromEncoded(it).toComposeImageBitmap())
                }
            }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "Postest",
            icon = iconPainter,
        ) {
            App()
        }
    }
}
