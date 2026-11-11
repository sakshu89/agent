package com.ultron.app.server

import android.content.Context
import com.ultron.app.LogStore
import com.ultron.app.LocalServerConfig
import com.ultron.app.AssistantController
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Small authenticated HTTP integration server. It is localhost-only unless explicitly configured otherwise. */
class LocalApiServer(private val context: Context) {
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()
    private var server: ServerSocket? = null
    @Volatile private var authToken: String? = null

    fun start(port: Int = LocalServerConfig.getApiPort(context), bindLocalhost: Boolean = LocalServerConfig.isLocalhostOnly(context)): Result<Int> = runCatching {
        if (running.get()) return Result.success(server?.localPort ?: port)
        require(port in 1024..65535) { "Port must be between 1024 and 65535." }
        val host = if (bindLocalhost) InetAddress.getLoopbackAddress() else InetAddress.getByName("0.0.0.0")
        server = ServerSocket(port, 50, host)
        authToken = LocalServerConfig.getApiToken(context).ifBlank { UUID.randomUUID().toString().also { LocalServerConfig.setApiToken(context, it) } }
        running.set(true)
        pool.execute { acceptLoop() }
        LogStore.a("SERVER", "Local API server started on ${server!!.inetAddress.hostAddress}:${server!!.localPort}")
        server!!.localPort
    }

    fun stop() {
        running.set(false)
        runCatching { server?.close() }
        server = null
        LogStore.a("SERVER", "Local API server stopped.")
    }

    fun isRunning() = running.get()
    fun token(): String = authToken ?: LocalServerConfig.getApiToken(context)

    private fun acceptLoop() {
        while (running.get()) {
            val socket = runCatching { server?.accept() }.getOrNull() ?: continue
            pool.execute { handle(socket) }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            s.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.UTF_8))
            val request = reader.readLine().orEmpty()
            val parts = request.split(' ')
            val method = parts.getOrNull(0).orEmpty()
            val path = parts.getOrNull(1).orEmpty().substringBefore('?')
            var line: String?
            var auth = ""
            var contentLength = 0
            while (reader.readLine().also { line = it } != null && !line.isNullOrEmpty()) {
                val h = line!!.split(':', limit = 2)
                if (h.size == 2 && h[0].equals("Authorization", true)) auth = h[1].trim()
                if (h.size == 2 && h[0].equals("Content-Length", true)) contentLength = h[1].trim().toIntOrNull() ?: 0
            }
            if (contentLength > 64 * 1024) return write(s, 413, "{\"error\":\"request too large\"}")
            val body = if (contentLength > 0) CharArray(contentLength).also { reader.read(it) }.concatToString() else ""
            val expected = "Bearer ${token()}"
            if (path != "/v1/health" && auth != expected) return write(s, 401, "{\"error\":\"unauthorized\"}")
            when {
                method == "GET" && path == "/v1/health" -> write(s, 200, "{\"ok\":true,\"service\":\"ultron-local-api\"}")
                method == "GET" && path == "/v1/status" -> write(s, 200, "{\"ok\":true,\"port\":${server?.localPort ?: -1},\"localhost_only\":${LocalServerConfig.isLocalhostOnly(context)}}")
                method == "POST" && path == "/v1/command" -> {
                    val text = runCatching { JSONObject(body).optString("text").trim() }.getOrDefault("")
                    if (text.isBlank()) write(s, 400, "{\"error\":\"JSON body must contain non-empty text\"}")
                    else { AssistantController.handleUserInput(text); write(s, 202, "{\"accepted\":true}") }
                }
                else -> write(s, 404, "{\"error\":\"not found\"}")
            }
            if (body.length > 0) LogStore.i("SERVER", "Received authenticated ${method} $path (${body.length} chars)")
        }
    }

    private fun write(socket: Socket, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write("HTTP/1.1 $code ${if (code == 200) "OK" else "Response"}\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
        out.write(bytes); out.flush()
    }
}
