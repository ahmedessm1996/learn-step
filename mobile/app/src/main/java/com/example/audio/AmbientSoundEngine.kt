package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.AmbientSoundType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Real-time procedural AudioTrack ambient sound synthesizer matching the 5 WebAudio soundscapes:
 * - WHITE: filtered noise
 * - BROWN: warm Brownian drift noise
 * - RAIN: gentle high-frequency drizzle
 * - FOREST: breezy modulated noise
 * - CAFE: warm low-passed murmur
 * Plus a subtle 660Hz sine completion chime.
 */
class AmbientSoundEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playJob: Job? = null
    private var track: AudioTrack? = null

    @Volatile
    private var currentVolume: Float = 0.55f

    @Volatile
    private var currentType: AmbientSoundType = AmbientSoundType.WHITE

    fun setVolume(volumePercent: Int) {
        currentVolume = (volumePercent.coerceIn(0, 100) / 100f)
    }

    fun start(type: AmbientSoundType, volumePercent: Int) {
        setVolume(volumePercent)
        if (currentType == type && playJob?.isActive == true) return
        stop()
        currentType = type
        playJob = scope.launch {
            runCatching { streamAmbient(type) }
        }
    }

    fun stop() {
        playJob?.cancel()
        playJob = null
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
    }

    fun playChime() {
        scope.launch {
            runCatching {
                val sampleRate = 22050
                val durationMs = 260
                val count = sampleRate * durationMs / 1000
                val buffer = ShortArray(count)
                for (i in 0 until count) {
                    val env = 1.0 - (i.toDouble() / count)
                    val sample = sin(2.0 * PI * 660.0 * i / sampleRate) * env * 0.25
                    buffer[i] = (sample * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                val chimeTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                chimeTrack.write(buffer, 0, buffer.size)
                chimeTrack.play()
                Thread.sleep(durationMs.toLong() + 40L)
                chimeTrack.release()
            }
        }
    }

    private fun streamAmbient(type: AmbientSoundType) {
        val sampleRate = 22050
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuf)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        track = audioTrack
        audioTrack.play()

        val buffer = ShortArray(1024)
        var brownLast = 0.0
        var lpLast = 0.0
        var hpLast = 0.0
        var hpPrevInput = 0.0
        var lfoPhase = 0.0

        while (scope.isActive && playJob?.isActive == true) {
            val gain = currentVolume * 0.28f
            for (i in buffer.indices) {
                val white = Random.nextDouble(-1.0, 1.0)
                val shaped = when (type) {
                    AmbientSoundType.WHITE -> {
                        lpLast = lpLast * 0.35 + white * 0.65
                        lpLast * 0.5
                    }
                    AmbientSoundType.BROWN -> {
                        brownLast = (brownLast + 0.02 * white) / 1.02
                        (brownLast * 3.2).coerceIn(-1.0, 1.0)
                    }
                    AmbientSoundType.RAIN -> {
                        val hp = 0.85 * (hpLast + white - hpPrevInput)
                        hpLast = hp
                        hpPrevInput = white
                        lpLast = lpLast * 0.65 + hp * 0.35
                        lpLast * 0.45
                    }
                    AmbientSoundType.FOREST -> {
                        lfoPhase += 2.0 * PI * 0.18 / sampleRate
                        val breeze = 0.45 + 0.45 * sin(lfoPhase)
                        lpLast = lpLast * 0.72 + white * 0.28
                        lpLast * breeze
                    }
                    AmbientSoundType.CAFE -> {
                        lfoPhase += 2.0 * PI * 0.42 / sampleRate
                        brownLast = (brownLast + 0.03 * white) / 1.03
                        val murmur = (brownLast * 2.4 + sin(lfoPhase) * 0.05)
                        lpLast = lpLast * 0.82 + murmur * 0.18
                        lpLast
                    }
                }
                val s = (shaped * gain * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                buffer[i] = s.toShort()
            }
            val written = audioTrack.write(buffer, 0, buffer.size)
            if (written < 0) break
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }
}
