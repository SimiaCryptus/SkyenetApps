package com.simiacryptus.skyenet

import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.awt.image.DirectColorModel
import java.net.URI
import javax.swing.ImageIcon
import javax.swing.SwingUtilities

class SystemTrayManager(
    private val port: Int,
    private val host: String,
    private val onExit: () -> Unit
) {
    private val log = LoggerFactory.getLogger(SystemTrayManager::class.java)
    private var trayIcon: TrayIcon? = null
    private var lastErrorTime: Long = 0
    private val ERROR_COOLDOWN = 5000 // 5 second cooldown between error messages
    private var lastErrorMessage: String? = null

    fun initialize() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray is not supported")
            return
        }

        SwingUtilities.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                val image = ImageIcon(javaClass.getResource("/icon.png")).image
                    ?: createDefaultImage()

                val popup = PopupMenu()
                
                val openItem = MenuItem("Open in Browser")
                openItem.addActionListener {
                    openInBrowser()
                }
                popup.add(openItem)

                popup.addSeparator()

                val exitItem = MenuItem("Exit")
                exitItem.addActionListener {
                    onExit()
                }
                popup.add(exitItem)

                trayIcon = TrayIcon(image, "SkyenetApps", popup).apply {
                    isImageAutoSize = true
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            if (e.button == MouseEvent.BUTTON1) {
                                openInBrowser()
                            }
                        }
                    })
                }

                tray.add(trayIcon)
                log.info("System tray icon initialized")
            } catch (e: Exception) {
                log.error("Failed to initialize system tray: ${e.message}", e)
                showError("Failed to initialize system tray")
            }
        }
    }

    private fun createDefaultImage(): Image {
        val size = 16
        val image = createRGBImage(size, size, true)
        return image
    }

    private fun createRGBImage(
        width: Int,
        height: Int, 
        hasAlpha: Boolean
    ): Image {
        val pixels = IntArray(width * height)
        val model = if (hasAlpha) {
            DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, -0x1000000)
        } else {
            DirectColorModel(24, 0x00FF0000, 0x0000FF00, 0x000000FF, 0)
        }
        val raster = model.createCompatibleWritableRaster(width, height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                raster.setPixel(x, y, model.getDataElements(pixels[y * width + x], null) as IntArray)
            }
        }
        return BufferedImage(model, raster, model.isAlphaPremultiplied, null)
    }

    private fun openInBrowser() {
        try {
            val url = "http://${if (host == "0.0.0.0") "localhost" else host}:$port"
            Desktop.getDesktop().browse(URI(url))
            log.info("Opened browser to $url")
        } catch (e: Exception) {
            log.error("Failed to open browser: ${e.message}", e)
            showError("Failed to open browser")
        }
    }
    private fun showError(message: String) {
        val now = System.currentTimeMillis()
        if (now - lastErrorTime > ERROR_COOLDOWN && message != lastErrorMessage) {
            trayIcon?.displayMessage(
                "Error",
                message,
                TrayIcon.MessageType.ERROR
            )
            lastErrorTime = now
            lastErrorMessage = message
        } else {
            log.debug("Suppressing error notification due to cooldown: $message")
        }
    }

    fun remove() {
        SwingUtilities.invokeLater {
            try {
                trayIcon?.let { SystemTray.getSystemTray().remove(it) }
                log.info("System tray icon removed")
            } catch (e: Exception) {
                log.error("Failed to remove system tray icon: ${e.message}", e)
                showError("Failed to remove system tray icon")
            }
        }
    }
}