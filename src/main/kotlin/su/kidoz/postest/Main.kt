package su.kidoz.postest

import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image
import org.koin.core.context.startKoin
import su.kidoz.postest.di.appModules
import su.kidoz.postest.util.AppVersion
import su.kidoz.postest.util.UpdateCheckResult
import su.kidoz.postest.util.UpdateChecker
import java.awt.Desktop
import java.awt.Taskbar
import java.net.URI
import javax.swing.JOptionPane

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
                JOptionPane.showMessageDialog(
                    null,
                    """
                    $APP_NAME
                    Version ${AppVersion.CURRENT}

                    A modern REST API client for testing and debugging HTTP APIs.

                    Developed with Kotlin and Compose Multiplatform.

                    © 2025 kidoz. All rights reserved.
                    """.trimIndent(),
                    "About $APP_NAME",
                    JOptionPane.INFORMATION_MESSAGE,
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
            MenuBar {
                Menu("Help") {
                    Item(
                        "Check for Updates...",
                        onClick = { performUpdateCheck() },
                        shortcut = KeyShortcut(Key.U, meta = true, shift = true),
                    )
                }
            }
            App()
        }
    }
}

/**
 * Performs the update check asynchronously and shows appropriate dialogs.
 */
private fun performUpdateCheck() {
    // Show a "Checking for updates..." dialog
    val progressDialog = javax.swing.JDialog()
    progressDialog.title = "Check for Updates"
    progressDialog.isModal = false
    progressDialog.setSize(300, 100)
    progressDialog.setLocationRelativeTo(null)
    progressDialog.add(javax.swing.JLabel("Checking for updates...", javax.swing.SwingConstants.CENTER))
    progressDialog.isVisible = true

    CoroutineScope(Dispatchers.IO).launch {
        val result = UpdateChecker.checkForUpdates()
        javax.swing.SwingUtilities.invokeLater {
            progressDialog.dispose()
            showUpdateResult(result)
        }
    }
}

/**
 * Shows the result of the update check to the user.
 */
private fun showUpdateResult(result: UpdateCheckResult) {
    when (result) {
        is UpdateCheckResult.UpdateAvailable -> {
            val info = result.updateInfo
            val message =
                buildString {
                    appendLine("A new version of $APP_NAME is available!")
                    appendLine()
                    appendLine("Current version: ${AppVersion.CURRENT}")
                    appendLine("New version: ${info.version}")
                    appendLine()
                    if (!info.body.isNullOrBlank()) {
                        appendLine("Release notes:")
                        appendLine(info.body.take(500))
                        if (info.body.length > 500) appendLine("...")
                    }
                }

            val options = arrayOf("Download", "View on GitHub", "Later")
            val choice =
                JOptionPane.showOptionDialog(
                    null,
                    message,
                    "Update Available",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    options,
                    options[0],
                )

            when (choice) {
                0 -> {
                    // Download - open direct download URL or releases page
                    val downloadUrl = info.downloadUrl ?: info.htmlUrl
                    openUrl(downloadUrl)
                }
                1 -> {
                    // View on GitHub
                    openUrl(info.htmlUrl)
                }
            }
        }

        is UpdateCheckResult.NoUpdateAvailable -> {
            JOptionPane.showMessageDialog(
                null,
                "You're running the latest version of $APP_NAME (${AppVersion.CURRENT}).",
                "No Updates Available",
                JOptionPane.INFORMATION_MESSAGE,
            )
        }

        is UpdateCheckResult.Error -> {
            JOptionPane.showMessageDialog(
                null,
                "Failed to check for updates:\n${result.message}\n\nPlease check your internet connection and try again.",
                "Update Check Failed",
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }
}

/**
 * Opens a URL in the default browser.
 */
private fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    } catch (e: Exception) {
        JOptionPane.showMessageDialog(
            null,
            "Could not open browser. Please visit:\n$url",
            "Error",
            JOptionPane.ERROR_MESSAGE,
        )
    }
}
