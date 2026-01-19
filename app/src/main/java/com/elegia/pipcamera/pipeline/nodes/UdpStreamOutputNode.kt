package com.elegia.pipcamera.pipeline.nodes

import android.content.Context
import android.util.Log
import com.elegia.pipcamera.pipeline.MediaData
import com.elegia.pipcamera.pipeline.OutputNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.*
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * High-performance UDP streaming output node
 * Handles proper packetization, sequencing, and error recovery
 */
class UdpStreamOutputNode(
    nodeId: String,
    private val targetHost: String = "192.168.1.100",
    private val targetPort: Int = 8000,
    private val maxPacketSize: Int = 1400, // Safe UDP size
    private val enableSequencing: Boolean = true
) : OutputNode(nodeId) {

    companion object {
        private const val TAG = "UdpStreamOutput"
        private const val HEADER_SIZE = 16 // bytes for packet header
    }

    private var socket: DatagramSocket? = null
    private var targetAddress: InetSocketAddress? = null
    private val sequenceNumber = AtomicLong(0)
    private val statistics = StreamingStatistics()

    override suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create UDP socket
            socket = DatagramSocket()

            // Resolve target address
            targetAddress = InetSocketAddress(targetHost, targetPort)

            Log.i(TAG, "UDP stream initialized: $targetHost:$targetPort, max packet: ${maxPacketSize} bytes")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize UDP stream", e)
            return@withContext false
        }
    }

    override suspend fun outputData(data: MediaData) = withContext(Dispatchers.IO) {
        when (data) {
            is MediaData.AudioFrame -> streamAudioFrame(data)
            is MediaData.EncodedData -> streamEncodedData(data)
            is MediaData.VideoFrame -> streamVideoFrame(data)
        }
    }

    private suspend fun streamEncodedData(data: MediaData.EncodedData) {
        val payload = data.buffer.array()
        val totalSize = payload.size

        // Calculate number of packets needed
        val maxPayloadSize = maxPacketSize - HEADER_SIZE
        val packetCount = (totalSize + maxPayloadSize - 1) / maxPayloadSize

        Log.v(TAG, "Streaming ${totalSize} bytes in ${packetCount} packets (codec: ${data.codecType})")

        for (packetIndex in 0 until packetCount) {
            val offset = packetIndex * maxPayloadSize
            val packetPayloadSize = minOf(maxPayloadSize, totalSize - offset)

            sendPacket(
                payload = payload,
                offset = offset,
                size = packetPayloadSize,
                packetIndex = packetIndex,
                totalPackets = packetCount,
                timestamp = data.timestamp,
                dataType = "encoded",
                codecInfo = data.codecType
            )
        }

        statistics.recordTransmission(totalSize, packetCount)
    }

    private suspend fun streamAudioFrame(data: MediaData.AudioFrame) {
        val payload = data.buffer.array()
        val totalSize = payload.size
        val maxPayloadSize = maxPacketSize - HEADER_SIZE
        val packetCount = (totalSize + maxPayloadSize - 1) / maxPayloadSize

        for (packetIndex in 0 until packetCount) {
            val offset = packetIndex * maxPayloadSize
            val packetPayloadSize = minOf(maxPayloadSize, totalSize - offset)

            sendPacket(
                payload = payload,
                offset = offset,
                size = packetPayloadSize,
                packetIndex = packetIndex,
                totalPackets = packetCount,
                timestamp = data.timestamp,
                dataType = "audio",
                codecInfo = "${data.sampleRate}Hz_${data.channels}ch"
            )
        }

        statistics.recordTransmission(totalSize, packetCount)
    }

    private suspend fun streamVideoFrame(data: MediaData.VideoFrame) {
        // Future video support
        Log.w(TAG, "Video streaming not yet implemented")
    }

    private suspend fun sendPacket(
        payload: ByteArray,
        offset: Int,
        size: Int,
        packetIndex: Int,
        totalPackets: Int,
        timestamp: Long,
        dataType: String,
        codecInfo: String
    ) {
        val sock = socket ?: return
        val addr = targetAddress ?: return

        try {
            // Create packet with header
            val packet = createPacketWithHeader(
                payload, offset, size, packetIndex, totalPackets, timestamp, dataType, codecInfo
            )

            val datagramPacket = DatagramPacket(packet, packet.size, addr)
            sock.send(datagramPacket)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send UDP packet", e)
            statistics.recordError()
        }
    }

    private fun createPacketWithHeader(
        payload: ByteArray,
        offset: Int,
        size: Int,
        packetIndex: Int,
        totalPackets: Int,
        timestamp: Long,
        dataType: String,
        codecInfo: String
    ): ByteArray {
        val header = ByteBuffer.allocate(HEADER_SIZE)

        if (enableSequencing) {
            header.putLong(sequenceNumber.incrementAndGet()) // 8 bytes: sequence number
        } else {
            header.putLong(timestamp) // 8 bytes: timestamp
        }

        header.putShort(packetIndex.toShort()) // 2 bytes: packet index
        header.putShort(totalPackets.toShort()) // 2 bytes: total packets
        header.putInt(size) // 4 bytes: payload size

        // Combine header and payload
        val packet = ByteArray(HEADER_SIZE + size)
        System.arraycopy(header.array(), 0, packet, 0, HEADER_SIZE)
        System.arraycopy(payload, offset, packet, HEADER_SIZE, size)

        return packet
    }

    override suspend fun cleanup() {
        socket?.close()
        socket = null
        targetAddress = null
        Log.i(TAG, "UDP stream cleanup completed. Stats: ${statistics.getSummary()}")
    }

    /**
     * Update streaming destination
     */
    fun updateDestination(host: String, port: Int) {
        targetAddress = InetSocketAddress(host, port)
        Log.i(TAG, "Updated destination: $host:$port")
    }

    /**
     * Get streaming statistics
     */
    fun getStatistics(): Map<String, Any> {
        return statistics.getDetailedStats()
    }
}

/**
 * Tracks UDP streaming performance metrics
 */
private class StreamingStatistics {
    private var totalBytes = 0L
    private var totalPackets = 0L
    private var errorCount = 0L
    private val startTime = System.currentTimeMillis()

    fun recordTransmission(bytes: Int, packets: Int) {
        totalBytes += bytes
        totalPackets += packets
    }

    fun recordError() {
        errorCount++
    }

    fun getSummary(): String {
        val runtime = (System.currentTimeMillis() - startTime) / 1000.0
        val bytesPerSecond = if (runtime > 0) totalBytes / runtime else 0.0
        val packetsPerSecond = if (runtime > 0) totalPackets / runtime else 0.0

        return "%.1f KB/s, %.1f pkt/s, %d errors".format(
            bytesPerSecond / 1024, packetsPerSecond, errorCount
        )
    }

    fun getDetailedStats(): Map<String, Any> {
        val runtime = (System.currentTimeMillis() - startTime) / 1000.0
        return mapOf(
            "totalBytes" to totalBytes,
            "totalPackets" to totalPackets,
            "errorCount" to errorCount,
            "runtimeSeconds" to runtime,
            "bytesPerSecond" to if (runtime > 0) totalBytes / runtime else 0.0,
            "packetsPerSecond" to if (runtime > 0) totalPackets / runtime else 0.0,
            "errorRate" to if (totalPackets > 0) errorCount.toDouble() / totalPackets else 0.0
        )
    }
}