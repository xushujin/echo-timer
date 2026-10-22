package com.echotimer.core.voice.offline

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** MFCC 提取参数。默认值按 16kHz 单声道语音调好，不建议在运行时随意改。 */
data class MfccConfig(
    val sampleRate: Int = 16_000,
    /** 25ms 一帧。 */
    val frameLength: Int = 400,
    /** 10ms 一跳。 */
    val hopLength: Int = 160,
    /** 512 点 FFT（400 帧长补零）。 */
    val fftSize: Int = 512,
    val melFilters: Int = 26,
    /** 保留多少阶倒谱系数（含 c0）。 */
    val cepstra: Int = 13,
    val lowFrequencyHz: Float = 80f,
    val highFrequencyHz: Float = 7_600f,
    /** 预加重系数，补偿语音高频衰减。 */
    val preEmphasis: Float = 0.97f,
) {
    init {
        require(fftSize >= frameLength && fftSize and (fftSize - 1) == 0) {
            "fftSize 必须是 2 的幂且不小于 frameLength"
        }
    }
}

/**
 * MFCC 特征提取（自研，不依赖任何语音库）。
 *
 * 流程：预加重 → 分帧 → 汉明窗 → FFT → 功率谱 → Mel 滤波器组 → 取对数 → DCT-II → 取前 N 阶 → CMN。
 *
 * 最后一步的 CMN（倒谱均值归一化）很关键：它把"麦克风频响 + 说话人音色 + 音量"这类
 * 与内容无关的常量偏移减掉，同一句话在不同距离/不同手机上仍然接近。
 *
 * 纯 Kotlin + 纯函数，可在 JVM 单测里跑。
 */
object Mfcc {

    fun extract(samples: ShortArray, config: MfccConfig = MfccConfig()): List<FloatArray> {
        if (samples.size < config.frameLength) return emptyList()

        val emphasized = preEmphasize(samples, config.preEmphasis)
        val frameCount = 1 + (samples.size - config.frameLength) / config.hopLength
        if (frameCount <= 0) return emptyList()

        val window = hamming(config.frameLength)
        val fft = Fft(config.fftSize)
        val filterBank = melFilterBank(config)
        val dct = dctMatrix(config.melFilters, config.cepstra)

        val re = FloatArray(config.fftSize)
        val im = FloatArray(config.fftSize)
        val power = FloatArray(config.fftSize / 2 + 1)
        val melLog = FloatArray(config.melFilters)

        val frames = ArrayList<FloatArray>(frameCount)
        for (index in 0 until frameCount) {
            val offset = index * config.hopLength
            java.util.Arrays.fill(re, 0f)
            java.util.Arrays.fill(im, 0f)
            for (i in 0 until config.frameLength) {
                re[i] = emphasized[offset + i] * window[i]
            }
            fft.transform(re, im)
            for (bin in power.indices) {
                power[bin] = re[bin] * re[bin] + im[bin] * im[bin]
            }
            for (m in 0 until config.melFilters) {
                var sum = 0f
                val start = filterBank.starts[m]
                val end = filterBank.ends[m]
                val weights = filterBank.weights[m]
                for (bin in start until end) {
                    sum += power[bin] * weights[bin - start]
                }
                melLog[m] = ln(sum + 1e-8f)
            }
            val cepstrum = FloatArray(config.cepstra)
            for (k in 0 until config.cepstra) {
                var sum = 0f
                val row = dct[k]
                for (m in 0 until config.melFilters) sum += row[m] * melLog[m]
                cepstrum[k] = sum
            }
            frames.add(cepstrum)
        }

        return cepstralMeanNormalize(frames)
    }

    /** 去掉首尾静音：按绝对值阈值裁剪，避免模板里混进大段无声。 */
    fun trimSilence(samples: ShortArray, threshold: Int = 600): ShortArray {
        var start = 0
        var end = samples.size - 1
        while (start < end && kotlin.math.abs(samples[start].toInt()) < threshold) start++
        while (end > start && kotlin.math.abs(samples[end].toInt()) < threshold) end--
        // end - start 是"长度 - 1"，这里要按实际长度判断。
        if (end - start + 1 < 400) return ShortArray(0)
        return samples.copyOfRange(start, end + 1)
    }

    private fun preEmphasize(samples: ShortArray, coefficient: Float): FloatArray {
        val out = FloatArray(samples.size)
        if (samples.isEmpty()) return out
        out[0] = samples[0].toFloat()
        for (i in 1 until samples.size) {
            out[i] = samples[i].toFloat() - coefficient * samples[i - 1]
        }
        return out
    }

    private fun hamming(length: Int): FloatArray =
        FloatArray(length) { i ->
            (0.54f - 0.46f * cos(2.0 * PI * i / (length - 1))).toFloat()
        }

    private fun cepstralMeanNormalize(frames: List<FloatArray>): List<FloatArray> {
        if (frames.size <= 1) return frames
        val dim = frames[0].size
        val mean = FloatArray(dim)
        for (frame in frames) {
            for (i in 0 until dim) mean[i] += frame[i]
        }
        for (i in 0 until dim) mean[i] /= frames.size
        return frames.map { frame ->
            FloatArray(dim) { i -> frame[i] - mean[i] }
        }
    }

    private class FilterBank(
        val starts: IntArray,
        val ends: IntArray,
        val weights: Array<FloatArray>,
    )

    private fun melFilterBank(config: MfccConfig): FilterBank {
        val bins = config.fftSize / 2 + 1
        val lowMel = mel(config.lowFrequencyHz)
        val highMel = mel(config.highFrequencyHz)
        val points = FloatArray(config.melFilters + 2) { i ->
            val m = lowMel + (highMel - lowMel) * i / (config.melFilters + 1)
            melToHz(m) * config.fftSize / config.sampleRate
        }
        val starts = IntArray(config.melFilters)
        val ends = IntArray(config.melFilters)
        val weights = Array(config.melFilters) { FloatArray(0) }

        for (m in 0 until config.melFilters) {
            val left = points[m]
            val center = points[m + 1]
            val right = points[m + 2]
            val start = kotlin.math.floor(left).toInt().coerceIn(0, bins - 1)
            val end = (kotlin.math.ceil(right).toInt() + 1).coerceIn(start + 1, bins)
            val row = FloatArray(end - start)
            for (bin in start until end) {
                val b = bin.toFloat()
                row[bin - start] = when {
                    b <= center -> if (center - left <= 0f) 0f else (b - left) / (center - left)
                    else -> if (right - center <= 0f) 0f else (right - b) / (right - center)
                }.coerceAtLeast(0f)
            }
            starts[m] = start
            ends[m] = end
            weights[m] = row
        }
        return FilterBank(starts, ends, weights)
    }

    private fun dctMatrix(filters: Int, cepstra: Int): Array<FloatArray> =
        Array(cepstra) { k ->
            FloatArray(filters) { n ->
                val scale = if (k == 0) sqrt(1f / filters) else sqrt(2f / filters)
                (scale * cos(PI * k * (n + 0.5) / filters)).toFloat()
            }
        }

    private fun mel(hz: Float): Float = (2595f * log10(1f + hz / 700f))
    private fun melToHz(mel: Float): Float = (700f * (10f.pow(mel / 2595f) - 1f))
}

/**
 * 迭代式 radix-2 FFT。只支持 2 的幂长度，够 MFCC 用；
 * 自己写是为了不引入任何第三方 DSP 库（与项目的"核心能力自研"约束一致）。
 */
internal class Fft(private val size: Int) {

    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val reversed = IntArray(size).also { table ->
        var bits = 0
        while ((1 shl bits) < size) bits++
        for (i in 0 until size) {
            var value = 0
            for (b in 0 until bits) {
                if (i and (1 shl b) != 0) value = value or (1 shl (bits - 1 - b))
            }
            table[i] = value
        }
    }

    fun transform(re: FloatArray, im: FloatArray) {
        for (i in 0 until size) {
            val j = reversed[i]
            if (j > i) {
                var tmp = re[i]; re[i] = re[j]; re[j] = tmp
                tmp = im[i]; im[i] = im[j]; im[j] = tmp
            }
        }
        var len = 2
        while (len <= size) {
            val half = len / 2
            val step = size / len
            var block = 0
            while (block < size) {
                var k = 0
                for (j in block until block + half) {
                    val cosValue = cosTable[k]
                    val sinValue = sinTable[k]
                    val tRe = re[j + half] * cosValue + im[j + half] * sinValue
                    val tIm = -re[j + half] * sinValue + im[j + half] * cosValue
                    re[j + half] = re[j] - tRe
                    im[j + half] = im[j] - tIm
                    re[j] += tRe
                    im[j] += tIm
                    k += step
                }
                block += len
            }
            len *= 2
        }
    }
}
