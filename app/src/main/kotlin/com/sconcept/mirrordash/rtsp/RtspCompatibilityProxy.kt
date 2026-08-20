package com.sconcept.mirrordash.rtsp

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RootEncoder's bundled RTSP server (1.3.x) loses its public IP/port when it clones its command
 * manager for each client, yielding `Content-Base: rtsp://:0/`. Home Assistant correctly rejects
 * that response. Keep the encoder on a private port and repair the RTSP negotiation at the LAN
 * endpoint without changing media packets or adding a second video encoder.
 */
class RtspCompatibilityProxy(
    private val publicPort: Int,
    private val upstreamPort: Int,
    private val publicIp: String,
) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newCachedThreadPool()
    private var serverSocket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        serverSocket = ServerSocket(publicPort)
        executor.execute {
            while (running.get()) {
                runCatching { serverSocket?.accept() }.getOrNull()?.let { client ->
                    executor.execute { bridge(client) }
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        executor.shutdownNow()
    }

    private fun bridge(client: Socket) {
        try {
            client.use { downstream: Socket ->
                Socket("127.0.0.1", upstreamPort).use { upstream: Socket ->
                val clientIn = BufferedInputStream(downstream.getInputStream())
                val clientOut = BufferedOutputStream(downstream.getOutputStream())
                val serverIn = BufferedInputStream(upstream.getInputStream())
                val serverOut = BufferedOutputStream(upstream.getOutputStream())
                while (running.get()) {
                    val request = readRtspMessage(clientIn) ?: return
                    val rewrittenRequest = request.replace(Regex("trackID=(\\d+)", RegexOption.IGNORE_CASE), "streamid=\$1")
                    serverOut.write(rewrittenRequest.toByteArray())
                    serverOut.flush()

                    val response = readRtspMessage(serverIn) ?: return
                    val rewrittenResponse = rewriteResponse(response)
                    clientOut.write(rewrittenResponse.toByteArray())
                    clientOut.flush()

                    if (request.startsWith("PLAY ", ignoreCase = true)) {
                        pipe(clientIn, serverOut)
                        pipe(serverIn, clientOut)
                        return
                    }
                }
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "RTSP client bridge ended", error)
        }
    }

    private fun rewriteResponse(response: String): String {
        val splitAt = response.indexOf("\r\n\r\n")
        if (splitAt < 0) return response
        var headers = response.substring(0, splitAt)
        var body = response.substring(splitAt + 4)
        headers = headers.replace(
            Regex("(?im)^Content-Base:.*$"),
            "Content-Base: rtsp://$publicIp:$publicPort/",
        )
        body = body
            .replace(Regex("(?im)^o=- 0 0 IN IP4 .*$"), "o=- 0 0 IN IP4 $publicIp")
            .replace(Regex("(?im)^c=IN IP4 .*$"), "c=IN IP4 $publicIp")
            .replace("a=control:trackID=", "a=control:streamid=")
        headers = headers.replace(Regex("(?im)^Content-Length:.*$"), "Content-Length: ${body.toByteArray().size}")
        return "$headers\r\n\r\n$body"
    }

    private fun readRtspMessage(input: BufferedInputStream): String? {
        val header = StringBuilder()
        var matched = 0
        while (true) {
            val value = input.read()
            if (value < 0) return null
            header.append(value.toChar())
            matched = when {
                value == "\r\n\r\n"[matched].code -> matched + 1
                value == "\r\n\r\n"[0].code -> 1
                else -> 0
            }
            if (matched == 4) break
        }
        val contentLength = Regex("(?im)^Content-Length:\\s*(\\d+)").find(header)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        if (contentLength == 0) return header.toString()
        val content = ByteArray(contentLength)
        var offset = 0
        while (offset < contentLength) {
            val read = input.read(content, offset, contentLength - offset)
            if (read < 0) return null
            offset += read
        }
        return header.append(String(content)).toString()
    }

    private fun pipe(input: BufferedInputStream, output: BufferedOutputStream) {
        executor.execute {
            runCatching {
                val buffer = ByteArray(16 * 1024)
                while (running.get()) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    output.flush()
                }
            }
        }
    }

    private companion object {
        const val TAG = "RtspCompatibilityProxy"
    }
}
