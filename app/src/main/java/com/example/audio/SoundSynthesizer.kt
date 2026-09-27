package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthétiseur audio Zen en temps réel.
 * - Goutte d'eau tombant dans l'eau (glide de fréquence rapide + onde de dispersion).
 * - Petit Gong grave à longue résonance vibrante.
 * - Occurrences aléatoires indépendantes sur des intervalles de 1 à 10 secondes.
 */
class SoundSynthesizer {

    private val sampleRate = 44100
    private var audioTrack: AudioTrack? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    // Volumes (réglés à mi-course : 50%)
    private val _waterDropVolume = MutableStateFlow(0.5f)
    val waterDropVolume: StateFlow<Float> = _waterDropVolume.asStateFlow()

    private val _gongVolume = MutableStateFlow(0.5f)
    val gongVolume: StateFlow<Float> = _gongVolume.asStateFlow()

    // Durée maximale du délai aléatoire (réglée à mi-course : 8.0s entre 2.0s et 15.0s)
    private val _maxDropDelayMs = MutableStateFlow(8000L)
    val maxDropDelayMs: StateFlow<Long> = _maxDropDelayMs.asStateFlow()
    val meanDropDelayMs: StateFlow<Long> get() = maxDropDelayMs // Rétrocompatibilité

    private val _maxGongDelayMs = MutableStateFlow(8000L)
    val maxGongDelayMs: StateFlow<Long> = _maxGongDelayMs.asStateFlow()
    val meanGongDelayMs: StateFlow<Long> get() = maxGongDelayMs // Rétrocompatibilité

    // Paramètres de timbre / fréquence
    private val _waterDropBaseFreq = MutableStateFlow(500f) // Fréquence de base (200Hz - 1000Hz)
    val waterDropBaseFreq: StateFlow<Float> = _waterDropBaseFreq.asStateFlow()

    private val _gongFrequency = MutableStateFlow(110f) // Fréquence fondamentale du Gong (60Hz - 220Hz)
    val gongFrequency: StateFlow<Float> = _gongFrequency.asStateFlow()

    interface AudioVoice {
        fun nextSample(): Float
        fun isFinished(): Boolean
    }

    /**
     * Voix de Goutte d'eau : glissement de fréquence exponentiel rapide et pur (effet "plop" cristallin).
     */
    inner class WaterDropVoice(
        private val volume: Float = 0.7f,
        private val baseFreq: Float = 500f,
        private val basePitchOffset: Float = 0f
    ) : AudioVoice {
        private var sampleCount = 0
        private val durationSec = 0.18f
        private val totalSamples = (durationSec * sampleRate).toInt()

        private val startFreq = baseFreq + basePitchOffset
        private val endFreq = (baseFreq * 3f) + basePitchOffset
        private var mainPhase = 0.0

        override fun nextSample(): Float {
            if (sampleCount >= totalSamples) return 0f

            val progress = sampleCount.toDouble() / totalSamples
            val t = sampleCount.toFloat() / sampleRate

            // Pitch glide exponentiel très fluide et naturel
            val currentFreq = startFreq + (endFreq - startFreq) * (progress * progress * progress)
            mainPhase += 2.0 * PI * currentFreq / sampleRate

            // Enveloppe d'amplitude : attaque rapide (2ms) puis déclin exponentiel rapide
            val attack = (t / 0.003f).coerceAtMost(1.0f)
            val decay = exp(-22f * t)
            val env = attack * decay

            val sample = sin(mainPhase) * env * 0.70f * volume

            sampleCount++
            return sample.toFloat().coerceIn(-1.0f, 1.0f)
        }

        override fun isFinished(): Boolean = sampleCount >= totalSamples
    }

    /**
     * Voix de Gong Grave : frappe de maillet riche et résonance métallique très grave, longue et vibrante.
     */
    inner class DeepGongVoice(
        private val volume: Float = 0.85f,
        private val baseFreq: Float = 110f // Gong très grave (La2 / 110 Hz)
    ) : AudioVoice {
        private var sampleCount = 0
        private val durationSec = 8.0f
        private val totalSamples = (durationSec * sampleRate).toInt()

        private var phase1 = 0.0
        private var phase2 = 0.0
        private var phase3 = 0.0
        private var phase4 = 0.0
        private var phase5 = 0.0

        override fun nextSample(): Float {
            if (sampleCount >= totalSamples) return 0f

            val t = sampleCount.toFloat() / sampleRate

            // Battements harmoniques riches et vibrants (effet gong bronze puissant)
            phase1 += 2.0 * PI * baseFreq / sampleRate             // Fondamentale (La2 / 110Hz)
            phase2 += 2.0 * PI * (baseFreq + 1.1f) / sampleRate    // Battement lent (Chaud)
            phase3 += 2.0 * PI * (baseFreq * 2.0f) / sampleRate    // Octave
            phase4 += 2.0 * PI * (baseFreq * 2.98f) / sampleRate   // Harmonique médium
            phase5 += 2.0 * PI * (baseFreq * 4.15f) / sampleRate   // Timbre brillant

            // Enveloppes de déclin pour une sonorité profonde et présente
            val env1 = exp(-0.35f * t)        // Fondamentale très longue (8 sec)
            val env2 = exp(-0.45f * t) * 0.85f // Battement vibrant
            val env3 = exp(-0.85f * t) * 0.50f // Octave claire
            val env4 = exp(-1.50f * t) * 0.30f // Médium métallique
            val env5 = exp(-2.80f * t) * 0.15f // Brillance initiale

            // Impact du maillet (très bref, franc)
            val attack = (t / 0.005f).coerceAtMost(1.0f)
            val malletThump = exp(-45f * t) * sin(2.0 * PI * (baseFreq * 0.6f) * t) * 0.20f

            // Somme pondérée et normalisée des harmoniques (somme des amplitudes max = 2.80)
            val rawResonance = sin(phase1) * env1 + sin(phase2) * env2 + sin(phase3) * env3 + sin(phase4) * env4 + sin(phase5) * env5
            val normalizedResonance = (rawResonance / 2.80f) * attack

            // Signal total sans dépasser la dynamique
            val rawSample = (normalizedResonance * 0.90f + malletThump) * volume

            // Compression / Limiteur doux (Soft Clipping via Tanh) pour éliminer la saturation numérique
            val sample = kotlin.math.tanh(rawSample.toDouble()).toFloat()

            sampleCount++
            return sample
        }

        override fun isFinished(): Boolean = sampleCount >= totalSamples
    }

    private val activeVoices = ConcurrentLinkedQueue<AudioVoice>()
    private val scope = CoroutineScope(Dispatchers.Default)

    private var audioLoopJob: Job? = null
    private var dropSchedulerJob: Job? = null
    private var gongSchedulerJob: Job? = null

    fun start() {
        if (_isPlaying.value) return

        // Nettoyage préventif au cas où un ancien AudioTrack existerait
        try {
            audioTrack?.let {
                if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            // Ignorer
        }
        audioTrack = null

        _isPlaying.value = true

        try {
            audioTrack = AudioTrack.Builder()
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
                .setBufferSizeInBytes(sampleRate * 2 / 10)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Boucle de mixage PCM en temps réel
        audioLoopJob = scope.launch {
            val bufferSize = 1024
            val buffer = ShortArray(bufferSize)

            while (_isPlaying.value) {
                val currentTrack = audioTrack
                if (currentTrack == null || currentTrack.state != AudioTrack.STATE_INITIALIZED) {
                    break
                }

                for (i in 0 until bufferSize) {
                    var sampleSum = 0f
                    val iterator = activeVoices.iterator()
                    while (iterator.hasNext()) {
                        val voice = iterator.next()
                        sampleSum += voice.nextSample()
                        if (voice.isFinished()) {
                            iterator.remove()
                        }
                    }

                    // Limiteur doux (soft clipping) pour éviter l'écrêtage dur au mixage PCM
                    val softLimited = kotlin.math.tanh(sampleSum.toDouble()).toFloat()
                    buffer[i] = (softLimited * 32767f).toInt().toShort()
                }

                try {
                    if (_isPlaying.value && currentTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val bytesWritten = currentTrack.write(buffer, 0, bufferSize)
                        if (bytesWritten < 0) {
                            break
                        }
                    } else {
                        break
                    }
                } catch (e: Exception) {
                    // AudioTrack a été arrêté ou libéré en cours de route
                    break
                }
            }
        }

        // Planificateur Goutte d'eau : occurrence aléatoire pure choisie librement entre 2s et maxDelayMs
        dropSchedulerJob = scope.launch {
            delay(500L)
            while (_isPlaying.value) {
                if (_waterDropVolume.value > 0.01f) {
                    triggerWaterDrop()
                }
                val maxDelay = _maxDropDelayMs.value.coerceAtLeast(2000L)
                val randomDelay = Random.nextLong(2000L, maxDelay + 1L)
                delay(randomDelay)
            }
        }

        // Planificateur Gong Grave : occurrence aléatoire pure choisie librement entre 2s et maxDelayMs
        gongSchedulerJob = scope.launch {
            delay(1500L)
            while (_isPlaying.value) {
                if (_gongVolume.value > 0.01f) {
                    triggerGong()
                }
                val maxDelay = _maxGongDelayMs.value.coerceAtLeast(2000L)
                val randomDelay = Random.nextLong(2000L, maxDelay + 1L)
                delay(randomDelay)
            }
        }
    }

    fun stop() {
        _isPlaying.value = false
        audioLoopJob?.cancel()
        dropSchedulerJob?.cancel()
        gongSchedulerJob?.cancel()

        activeVoices.clear()

        val trackToRelease = audioTrack
        audioTrack = null

        try {
            if (trackToRelease != null) {
                if (trackToRelease.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    trackToRelease.stop()
                }
                trackToRelease.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun triggerWaterDrop() {
        if (!_isPlaying.value) start()
        val pitchVariation = Random.nextFloat() * 120f - 60f
        activeVoices.add(
            WaterDropVoice(
                volume = _waterDropVolume.value,
                baseFreq = _waterDropBaseFreq.value,
                basePitchOffset = pitchVariation
            )
        )
    }

    fun triggerGong() {
        if (!_isPlaying.value) start()
        activeVoices.add(
            DeepGongVoice(
                volume = _gongVolume.value,
                baseFreq = _gongFrequency.value
            )
        )
    }

    fun setWaterDropVolume(vol: Float) {
        _waterDropVolume.value = vol.coerceIn(0f, 1f)
    }

    fun setGongVolume(vol: Float) {
        _gongVolume.value = vol.coerceIn(0f, 1f)
    }

    fun setWaterDropBaseFreq(freq: Float) {
        _waterDropBaseFreq.value = freq.coerceIn(200f, 1200f)
    }

    fun setGongFrequency(freq: Float) {
        _gongFrequency.value = freq.coerceIn(50f, 250f)
    }

    fun setDropMaxDelay(maxMs: Long) {
        _maxDropDelayMs.value = maxMs.coerceIn(2000L, 20000L)
    }

    fun setGongMaxDelay(maxMs: Long) {
        _maxGongDelayMs.value = maxMs.coerceIn(2000L, 20000L)
    }

    fun setDropMeanDelay(meanMs: Long) = setDropMaxDelay(meanMs)
    fun setGongMeanDelay(meanMs: Long) = setGongMaxDelay(meanMs)
}
