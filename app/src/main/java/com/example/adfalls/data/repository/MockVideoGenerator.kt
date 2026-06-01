package com.example.adfalls.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

object MockVideoGenerator {
    private const val WIDTH = 320
    private const val HEIGHT = 180
    private const val FRAME_RATE = 10
    private const val DURATION_SECONDS = 15
    private const val BIT_RATE = 300_000
    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC

    private val specs = listOf(
        VideoSpec("adfalls_mock_blue.mp4", 52, 126, 246),
        VideoSpec("adfalls_mock_green.mp4", 36, 168, 116),
        VideoSpec("adfalls_mock_orange.mp4", 239, 128, 64)
    )

    fun ensureVideos(context: Context): List<String> {
        val dir = File(context.filesDir, "mock_videos").apply { mkdirs() }
        return specs.map { spec ->
            val file = File(dir, spec.fileName)
            if (!file.exists() || file.length() < 1024L) {
                createVideo(file, spec)
            }
            file.toURI().toString()
        }
    }

    private fun createVideo(output: File, spec: VideoSpec) {
        val temp = File(output.parentFile, "${output.name}.tmp")
        temp.delete()

        val selection = selectAvcEncoder()
        val codecInfo = selection.codecInfo
        val colorFormat = selection.colorFormat
        val format = MediaFormat.createVideoFormat(MIME_TYPE, WIDTH, HEIGHT).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }

        val codec = MediaCodec.createByCodecName(codecInfo.name)
        val muxer = MediaMuxer(temp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bufferInfo = MediaCodec.BufferInfo()
        val state = MuxerState()

        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()

            val totalFrames = FRAME_RATE * DURATION_SECONDS
            for (frame in 0 until totalFrames) {
                queueFrame(codec, colorFormat, spec, frame)
                drain(codec, muxer, bufferInfo, state, endOfStream = false)
            }
            queueEndOfStream(codec, totalFrames)
            drain(codec, muxer, bufferInfo, state, endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { muxer.stop() }
            muxer.release()
        }

        if (output.exists()) output.delete()
        temp.renameTo(output)
    }

    private fun queueFrame(codec: MediaCodec, colorFormat: Int, spec: VideoSpec, frame: Int) {
        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex < 0) return
        val input = codec.getInputBuffer(inputIndex) ?: return
        input.clear()
        fillFrame(input, colorFormat, spec, frame)
        codec.queueInputBuffer(inputIndex, 0, frameSize(), presentationTimeUs(frame), 0)
    }

    private fun queueEndOfStream(codec: MediaCodec, totalFrames: Int) {
        val inputIndex = codec.dequeueInputBuffer(10_000)
        if (inputIndex >= 0) {
            codec.queueInputBuffer(
                inputIndex,
                0,
                0,
                presentationTimeUs(totalFrames),
                MediaCodec.BUFFER_FLAG_END_OF_STREAM
            )
        }
    }

    private fun drain(
        codec: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        state: MuxerState,
        endOfStream: Boolean
    ) {
        while (true) {
            when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    state.trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    state.started = true
                }
                else -> if (outputIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outputIndex)
                    if (encoded != null && bufferInfo.size > 0) {
                        check(state.started) { "Muxer has not started." }
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(state.trackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun fillFrame(buffer: ByteBuffer, colorFormat: Int, spec: VideoSpec, frame: Int) {
        val pulse = ((frame % FRAME_RATE).toFloat() / FRAME_RATE * 36f).roundToInt()
        val yuv = rgbToYuv(
            r = (spec.r + pulse).coerceAtMost(255),
            g = (spec.g + pulse).coerceAtMost(255),
            b = (spec.b + pulse).coerceAtMost(255)
        )
        val ySize = WIDTH * HEIGHT
        val uvSize = ySize / 4

        repeat(ySize) { buffer.put(yuv.y.toByte()) }
        when (colorFormat) {
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible -> {
                repeat(uvSize) { buffer.put(yuv.u.toByte()) }
                repeat(uvSize) { buffer.put(yuv.v.toByte()) }
            }
            else -> {
                repeat(uvSize) {
                    buffer.put(yuv.u.toByte())
                    buffer.put(yuv.v.toByte())
                }
            }
        }
    }

    private fun selectAvcEncoder(): EncoderSelection {
        return android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .asSequence()
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MIME_TYPE, true) } }
            .mapNotNull { info ->
                val supported = info.getCapabilitiesForType(MIME_TYPE).colorFormats.toSet()
                val colorFormat = when {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar in supported ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar in supported ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in supported ->
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
                    else -> null
                }
                colorFormat?.let { EncoderSelection(info, it) }
            }
            .first()
    }

    private fun rgbToYuv(r: Int, g: Int, b: Int): YuvColor {
        val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
        val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
        val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
        return YuvColor(y.coerceIn(0, 255), u.coerceIn(0, 255), v.coerceIn(0, 255))
    }

    private fun frameSize(): Int = WIDTH * HEIGHT * 3 / 2

    private fun presentationTimeUs(frameIndex: Int): Long = frameIndex * 1_000_000L / FRAME_RATE

    private data class VideoSpec(val fileName: String, val r: Int, val g: Int, val b: Int)
    private data class YuvColor(val y: Int, val u: Int, val v: Int)
    private data class MuxerState(var started: Boolean = false, var trackIndex: Int = -1)
    private data class EncoderSelection(val codecInfo: MediaCodecInfo, val colorFormat: Int)
}
