package com.simiacryptus.skyenet

import com.simiacryptus.skyenet.core.platform.Session
import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.ImageTranscoder
import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.net.URI
import javax.swing.SwingUtilities
import com.simiacryptus.skyenet.webui.application.ApplicationDirectory.ChildWebApp
import javax.swing.JOptionPane

class SystemTrayManager(
    private val port: Int,
    private val host: String,
    private val onExit: () -> Unit,
    private val apps: List<ChildWebApp> = emptyList()
) {
    private val log = LoggerFactory.getLogger(SystemTrayManager::class.java)
    private var trayIcon: TrayIcon? = null
    private var lastErrorTime: Long = 0
    private val ERROR_COOLDOWN = 5000 // 5 second cooldown between error messages
    private var lastErrorMessage: String? = null
    private fun loadSvgImage(): Image? {
        return try {
            val svgStream = javaClass.getResourceAsStream("/icons/toolbarIcon.svg")
            if (svgStream == null) {
                log.warn("Could not find toolbarIcon.svg")
                null
            } else {
                val transcoder = object : ImageTranscoder() {
                    var image: BufferedImage? = null
                    override fun createImage(w: Int, h: Int): BufferedImage {
                        return BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                    }
                    override fun writeImage(img: BufferedImage, output: TranscoderOutput?) {
                        this.image = img
                    }
                }
                transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, 32f)
                transcoder.addTranscodingHint(ImageTranscoder.KEY_HEIGHT, 32f)
                transcoder.transcode(TranscoderInput(svgStream), TranscoderOutput())
                transcoder.image
            }
        } catch (e: Exception) {
            log.error("Failed to load SVG image: ${e.message}", e)
            null
        }
    }


    fun initialize() {
        if (!SystemTray.isSupported()) {
            log.warn("System tray is not supported")
            return
        }

        SwingUtilities.invokeLater {
            try {
                val tray = SystemTray.getSystemTray()
                val image = loadSvgImage()
                val popup = PopupMenu()
                // Add Applications submenu
                if (apps.isNotEmpty()) {
                    popup.addSeparator()
                    apps.forEach { app ->
                        val item = MenuItem(app.server.applicationName)
                        item.addActionListener {
                            openInBrowser("${app.path}/#" + Session.newUserID())
                        }
                        popup.add(item)
                    }
                }
                
                popup.addSeparator()
                val exitItem = MenuItem("Exit")
                exitItem.addActionListener {
                    confirm("Exit?") {
                        onExit()
                    }
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

    private fun confirm(message: String, onConfirm: () -> Unit) {
        val result = JOptionPane.showConfirmDialog(
            null,
            message,
            "SkyenetApps",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        )
        if (result == JOptionPane.YES_OPTION) {
            onConfirm()
        }
    }


    private fun openInBrowser(path: String = "") {
        try {
            val url = "http://${if (host == "0.0.0.0") "localhost" else host}:$port$path"
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