package com.ullim.watch.settings

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.Executors

/** Temporary, local-only settings controller. Never receives or exposes microphone audio. */
class LocalTuningServer(
    private val page: ByteArray,
    private val snapshot: () -> String,
    private val command: (String) -> String,
    private val preferredPort: Int = 8765,
) : Closeable {
    private var socket: ServerSocket? = null
    private var executor = Executors.newSingleThreadExecutor()
    private var token = ""
    @Volatile private var running = false
    val port: Int get() = socket?.localPort ?: 0

    @Synchronized fun start(): String {
        if (running) return path()
        token = ByteArray(6).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        socket = runCatching { ServerSocket(preferredPort) }.getOrElse { ServerSocket(0) }
        running = true
        if (executor.isShutdown) executor = Executors.newSingleThreadExecutor()
        val listener = socket!!
        executor.execute {
            while (running) {
                val client = try { listener.accept() } catch (_: Exception) { break }
                client.use { runCatching { handle(it) } }
            }
        }
        return path()
    }

    private fun path() = "/$token/"
    fun addresses(): List<String> = NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }.flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>().filter { it.isSiteLocalAddress }
        .map { "http://${it.hostAddress}:$port${path()}" }.distinct()

    private fun handle(client: Socket) {
        client.soTimeout = 1500
        val input = client.getInputStream().buffered()
        val request = line(input)?.split(' ') ?: return
        if (request.size != 3) return
        val headers = mutableMapOf<String, String>()
        var total = 0
        while (true) {
            val next = line(input) ?: return
            total += next.length
            if (total > 8192) return
            if (next.isEmpty()) break
            val separator = next.indexOf(':')
            if (separator > 0) headers[next.take(separator).lowercase()] = next.substring(separator + 1).trim()
        }
        val method = request[0]; val target = request[1]
        if (method == "GET" && target == path()) { respond(client, 200, "text/html", page); return }
        if (target != path() + "api") { respond(client, 404, "text/plain", "Not found".toByteArray()); return }
        if (method == "GET") { respond(client, 200, "application/json", snapshot().toByteArray(Charsets.UTF_8)); return }
        if (method != "POST" || headers["content-type"]?.startsWith("application/json") != true ||
            headers["origin"]?.let { it != "http://${headers["host"]}" } == true) {
            respond(client, 403, "text/plain", "Forbidden".toByteArray()); return
        }
        val length = headers["content-length"]?.toIntOrNull()
        if (length == null || length !in 1..8192) { respond(client, 400, "text/plain", "Invalid size".toByteArray()); return }
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) { val count = input.read(body, offset, length - offset); if (count < 0) return; offset += count }
        try { respond(client, 200, "application/json", command(body.toString(Charsets.UTF_8)).toByteArray(Charsets.UTF_8)) }
        catch (_: IllegalArgumentException) { respond(client, 400, "text/plain", "Invalid setting".toByteArray()) }
    }

    private fun line(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 2048) {
            val byte = input.read()
            if (byte < 0) return null
            if (byte == 10) return bytes.toString("US-ASCII").trimEnd('\r')
            bytes.write(byte)
        }
        return null
    }

    private fun respond(client: Socket, code: Int, type: String, body: ByteArray) {
        val status = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; else -> "Not Found" }
        val header = "HTTP/1.1 $code $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(body); flush() }
    }

    @Synchronized override fun close() {
        running = false
        runCatching { socket?.close() }
        socket = null
        executor.shutdownNow()
    }
}
