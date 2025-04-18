package com.simiacryptus.skyenet

import org.slf4j.LoggerFactory
import java.io.*
import java.net.Socket
import java.net.ConnectException
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.concurrent.thread

/**
 * Entry point for the daemon client.
 * This will launch the AppServer as a separate process (daemon) if needed,
 * reconnect if possible, and dispatch commands to the server.
 */
object DaemonClient {
    private const val DEFAULT_PORT = 7681
    private const val DEFAULT_HOST = "localhost"
    private const val PID_FILE = "skyenet_server.pid"

    @JvmStatic
    fun main(args: Array<String>) {
        val port = DEFAULT_PORT
        val host = DEFAULT_HOST
        log.info("DaemonClient started with args: ${args.joinToString(" ")}")
        if (!isServerRunning(host, port)) {
            log.info("Server not running. Launching daemon...")
            println("Server not running. Launching daemon...")
            launchDaemon(port)
            waitForServer(host, port)
        } else {
            log.info("Server already running.")
            println("Server already running.")
        }
        if (args.isNotEmpty()) {
            log.info("Dispatching command: ${args.joinToString(" ")}")
            dispatchCommand(host, port, args)
        } else {
            log.warn("No command specified. Use: daemonclient <command> [args]")
            println("No command specified. Use: daemonclient <command> [args]")
        }
    }

    private fun isServerRunning(host: String, port: Int): Boolean {
        return try {
            log.debug("Checking if server is running at $host:$port")
            Socket(host, port).use { true }
        } catch (e: ConnectException) {
            log.debug("Server is not running at $host:$port: ${e.message}")
            false
        } catch (e: Exception) {
            log.warn("Unexpected error while checking server status: ${e.message}", e)
            false
        }
    }

    private fun waitForServer(host: String, port: Int, timeoutMs: Long = 10000L) {
        val start = System.currentTimeMillis()
        log.info("Waiting for server to start at $host:$port (timeout=${timeoutMs}ms)")
        while (!isServerRunning(host, port)) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                log.error("Timed out waiting for server to start at $host:$port")
                throw RuntimeException("Timed out waiting for server to start")
            }
            Thread.sleep(200)
        }
        log.info("Server is now running at $host:$port")
        println("Server is now running.")
    }

    private fun launchDaemon(port: Int) {
        val javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java"
        val classpath = System.getProperty("java.class.path")
        val className = "com.simiacryptus.skyenet.AppServer"
        val processBuilder = ProcessBuilder(
            javaBin, "-cp", classpath, className, "server", "--port", port.toString()
        )
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
        log.info("Launching daemon process: $javaBin -cp $classpath $className server --port $port")
        try {
            val process = processBuilder.start()
            log.info("Daemon process started with PID: ${process.pid()}")
            // Optionally, write the PID to a file
            writePidFile(process)
            thread(isDaemon = true, name = "DaemonClient-ProcessWaiter") {
                val exitCode = process.waitFor()
                log.warn("Server process exited with code $exitCode.")
                println("Server process exited.")
                deletePidFile()
            }
        } catch (e: Exception) {
            log.error("Failed to launch daemon process: ${e.message}", e)
            throw e
        }
    }

    private fun writePidFile(process: Process) {
        try {
            val pid = process.pid()
            Files.write(Paths.get(PID_FILE), pid.toString().toByteArray())
            log.info("Wrote PID file: $PID_FILE with PID $pid")
        } catch (e: Exception) {
            log.warn("Could not write PID file: ${e.message}", e)
            println("Warning: Could not write PID file: ${e.message}")
        }
    }

    private fun deletePidFile() {
        try {
            Files.deleteIfExists(Paths.get(PID_FILE))
            log.info("Deleted PID file: $PID_FILE")
        } catch (e: Exception) {
            log.debug("Could not delete PID file: ${e.message}")
            // ignore
        }
    }

    private fun dispatchCommand(host: String, port: Int, args: Array<String>) {
        // Send the command as a string to the server and print the response.
        try {
            log.debug("Connecting to server at $host:$port to dispatch command: ${args.joinToString(" ")}")
            Socket(host, port).use { socket ->
                val out = PrintWriter(socket.getOutputStream(), true)
                val input = BufferedReader(InputStreamReader(socket.getInputStream()))
                val command = args.joinToString(" ")
                out.println(command)
                log.info("Sent command: $command")
                println("Sent command: $command")
                // Read response (if any)
                val response = input.readLine()
                if (response != null) {
                    log.info("Received server response: $response")
                    println("Server response: $response")
                } else {
                    log.warn("No response received from server.")
                    println("No response received from server.")
                }
            }
        } catch (e: Exception) {
            log.error("Failed to dispatch command: ${e.message}", e)
            println("Failed to dispatch command: ${e.message}")
        }
    }

    val log = LoggerFactory.getLogger(DaemonClient::class.java)
}