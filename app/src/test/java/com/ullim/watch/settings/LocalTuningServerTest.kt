package com.ullim.watch.settings

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

class LocalTuningServerTest {
    @Test fun servesGraphAndSettingsAcceptsCommandsAndClosesWithoutAudio() {
        val writes = AtomicInteger()
        LocalTuningServer("<html>graph</html>".toByteArray(), { "{\"saved\":true}" }, {
            writes.incrementAndGet(); "{\"accepted\":true}"
        }, preferredPort = 0).use { server ->
            val path = server.start()
            val base = "http://127.0.0.1:${server.port}$path"
            assertEquals("<html>graph</html>", URL(base).readText())
            assertEquals("{\"saved\":true}", URL(base + "api").readText())
            val post = URL(base + "api").openConnection() as HttpURLConnection
            post.requestMethod = "POST"; post.doOutput = true; post.setRequestProperty("Content-Type", "application/json")
            post.outputStream.use { it.write("{\"action\":\"set\"}".toByteArray()) }
            assertEquals(200, post.responseCode)
            assertEquals("{\"accepted\":true}", post.inputStream.bufferedReader().use { it.readText() })
            assertEquals(1, writes.get())
            assertEquals(404, (URL("http://127.0.0.1:${server.port}/api").openConnection() as HttpURLConnection).responseCode)
            val denied = URL(base + "api").openConnection() as HttpURLConnection
            denied.requestMethod = "POST"; denied.doOutput = true; denied.setRequestProperty("Content-Type", "text/plain")
            denied.outputStream.use { it.write("reset".toByteArray()) }
            assertEquals(403, denied.responseCode)
            assertEquals(1, writes.get())
        }
    }
}
