package com.example.litcompose.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 音频频谱分析器：挂在 ExoPlayer 音频处理链上直接截取 PCM 数据做 FFT，
 * 按对数频率分成 [barCount] 桶实时输出，驱动播放页波形动效。
 * 不依赖系统 Visualizer effect 引擎，兼容所有设备。
 */
@OptIn(UnstableApi::class)
class SpectrumAudioProcessor(
    private val barCount: Int = 28,
) : BaseAudioProcessor() {

    private val _spectrum = MutableStateFlow(FloatArray(barCount))
    val spectrum: StateFlow<FloatArray> = _spectrum.asStateFlow()

    // FFT 窗口：2048 点（44.1kHz 下约 46ms 一次频谱）
    // 窗口越大频率分辨率越高：256 点时最低 bin 约 172Hz，对数分桶 20Hz~20kHz 下
    // 前 8 个低音桶没有任何数据点、恒为 0，表现为最左侧竖条不跳动
    private val fftSize = 2048
    private val windowSamples = FloatArray(fftSize)
    private var windowCount = 0

    /** 透传：输出格式保持与输入一致，不改变音频数据 */
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        inputAudioFormat

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // 透传：把输入完整拷贝到输出 buffer，音频原样继续流向后续处理（静音跳过/变速）
        val out = replaceOutputBuffer(inputBuffer.remaining())
        val savedOrder = inputBuffer.order()
        inputBuffer.order(ByteOrder.nativeOrder())
        out.put(inputBuffer)
        inputBuffer.order(savedOrder)
        out.flip()
        // 消费输入：position 推到 limit，通知 pipeline 本段已处理
        inputBuffer.position(inputBuffer.limit())
        analyze(out)
    }

    override fun onFlush() {
        windowCount = 0
    }

    override fun onReset() {
        windowCount = 0
    }

    /** 累积 PCM 样本到 FFT 窗口，攒满 [fftSize] 个计算一次频谱 */
    private fun analyze(buffer: ByteBuffer) {
        val format = inputAudioFormat
        val sampleRate = format.sampleRate
        val encoding = format.encoding
        if (sampleRate <= 0 || (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT)) {
            return
        }
        val channelCount = if (format.channelCount > 0) format.channelCount else 1
        val floats = windowSamples
        var count = windowCount
        // duplicate：独立 position，不影响透传输出 buffer 的读取进度
        val dup = buffer.duplicate()
        dup.order(ByteOrder.LITTLE_ENDIAN)
        if (encoding == C.ENCODING_PCM_16BIT) {
            while (dup.remaining() >= 2 * channelCount && count < fftSize) {
                dup.position(dup.position() + 2 * (channelCount - 1)) // 多声道时跳到第一声道
                floats[count++] = dup.short.toFloat() / 32768f
            }
        } else {
            while (dup.remaining() >= 4 * channelCount && count < fftSize) {
                dup.position(dup.position() + 4 * (channelCount - 1))
                floats[count++] = dup.float
            }
        }
        windowCount = count
        if (windowCount == fftSize) {
            computeWindow()
            windowCount = 0
        }
    }

    private fun computeWindow() {
        val n = fftSize
        val real = windowSamples
        // Hann 窗抑制频谱泄漏
        for (i in 0 until n) {
            real[i] *= 0.5f * (1f - cos(2.0 * PI * i / n).toFloat())
        }
        val imag = FloatArray(n)
        fft(real, imag, n)
        // 对数频率分桶（20Hz~20kHz），取每桶最大幅度并平方根压缩
        val nyquist = inputAudioFormat.sampleRate / 2f
        val logMax = log10(20_000f / 20f)
        val out = FloatArray(barCount)
        val half = n / 2
        for (bin in 1 until half) {
            val mag = sqrt(real[bin] * real[bin] + imag[bin] * imag[bin])
            val amp = (mag / n).coerceIn(0f, 1f)
            val freq = bin.toFloat() * nyquist / half
            if (freq <= 20f) continue
            val bar = (log10(freq / 20f) / logMax * barCount).toInt().coerceIn(0, barCount - 1)
            if (amp > out[bar]) out[bar] = amp
            // 低频端桶宽（约 1.27 倍频程）小于 FFT bin 间隔，存在无数据点落进的空桶；
            // 把低频能量向左渐变扩散（距源桶越远越弱），保证最左侧柱子随低音跳动又不呆板
            if (bar <= 3) {
                for (j in 0 until bar) {
                    val spill = amp * 0.5f * (bar - j + 1).toFloat() / (bar + 1).toFloat()
                    if (spill > out[j]) out[j] = spill
                }
            }
        }
        for (i in out.indices) out[i] = sqrt(out[i])
        _spectrum.value = out
    }

    /** 迭代 radix-2 FFT（n 为 2 的幂），就地计算，结果写回 real/imag */
    private fun fft(real: FloatArray, imag: FloatArray, n: Int) {
        // 位反转重排
        var i = 0
        while (i < n) {
            var j = 0
            var m = i
            var k = 1
            while (k < n) {
                j = (j shl 1) or (m and 1)
                m = m shr 1
                k = k shl 1
            }
            if (j > i) {
                val tr = real[i]; real[i] = real[j]; real[j] = tr
                val ti = imag[i]; imag[i] = imag[j]; imag[j] = ti
            }
            i++
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var base = 0
            while (base < n) {
                var curRe = 1f
                var curIm = 0f
                var j = 0
                while (j < len / 2) {
                    val uRe = real[base + j]
                    val uIm = imag[base + j]
                    val vRe = real[base + j + len / 2] * curRe - imag[base + j + len / 2] * curIm
                    val vIm = real[base + j + len / 2] * curIm + imag[base + j + len / 2] * curRe
                    real[base + j] = uRe + vRe
                    imag[base + j] = uIm + vIm
                    real[base + j + len / 2] = uRe - vRe
                    imag[base + j + len / 2] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    val nextIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                    curIm = nextIm
                    j++
                }
                base += len
            }
            len = len shl 1
        }
    }
}
