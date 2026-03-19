package com.projectmaidgroup.platform.sherpa_onnx_asr

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult
import com.k2fsa.sherpa.onnx.OnlineStream

/**
 * 轻量 Kotlin 封装：
 * - 负责加载 sherpa-onnx ASR 模型
 * - 提供流式/分段音频喂入与拿结果的调用方式
 *
 * 注意：该模块只封装 Java/Kotlin API；你仍需要把 `libsherpa-onnx-jni.so`
 * 以及（通常还包括）`libonnxruntime.so` 放到宿主 App 的 `src/main/jniLibs/<abi>/` 中。
 */
class SherpaOnnxAsr private constructor(
    private val recognizer: OnlineRecognizer,
) {
    data class ModelPaths(
        val tokens: String,
        val encoder: String? = null,
        val decoder: String? = null,
        val joiner: String? = null,
        val paraformerEncoder: String? = null,
        val paraformerDecoder: String? = null,
        val zipformer2CtcModel: String? = null,
        val neMoCtcModel: String? = null,
        val toneCtcModel: String? = null,
        /**
         * sherpa-onnx 的 `modelType` 取值取决于你选用的模型：
         * 常见如：zipformer/zipformer2/lstm/paraformer
         */
        val modelType: String = "",
    )

    data class Options(
        val sampleRate: Int = 16000,
        val featureDim: Int = 80,
        val numThreads: Int = 2,
        val enableEndpoint: Boolean = true,
        val decodingMethod: String = "greedy_search",
        val maxActivePaths: Int = 4,
    )

    class Session internal constructor(
        private val recognizer: OnlineRecognizer,
        private val stream: OnlineStream,
    ) {
        /**
         * 喂入 PCM float（范围建议 [-1, 1]），采样率与创建 recognizer 的 sampleRate 对齐。
         */
        fun acceptPcmFloat(pcm: FloatArray, sampleRate: Int) {
            stream.acceptWaveform(pcm, sampleRate)
        }

        fun inputFinished() {
            stream.inputFinished()
        }

        /**
         * 尝试解码到当前可用的结果（非阻塞式地跑几步）。
         * 建议：录音线程喂数据；另一个线程/协程周期性调用该方法拿文本。
         */
        fun decodeAvailable() {
            while (recognizer.isReady(stream)) {
                recognizer.decode(stream)
            }
        }

        fun isEndpoint(): Boolean = recognizer.isEndpoint(stream)

        fun getResult(): OnlineRecognizerResult = recognizer.getResult(stream)

        fun reset() {
            recognizer.reset(stream)
        }

        fun close() {
            stream.release()
        }
    }

    fun newSession(hotwords: String = ""): Session {
        val stream = recognizer.createStream(hotwords)
        return Session(recognizer, stream)
    }

    fun close() {
        recognizer.release()
    }

    companion object {
        /**
         * 从 assets 加载模型（推荐）。
         * 你需要把模型文件放入宿主 App 的 `src/main/assets/` 下，然后 paths 用相对路径指向它们。
         */
        fun createFromAssets(
            context: Context,
            paths: ModelPaths,
            options: Options = Options(),
        ): SherpaOnnxAsr {
            val modelConfig = OnlineModelConfig(
                tokens = paths.tokens,
                numThreads = options.numThreads,
                modelType = paths.modelType,
            ).apply {
                transducer.encoder = paths.encoder.orEmpty()
                transducer.decoder = paths.decoder.orEmpty()
                transducer.joiner = paths.joiner.orEmpty()

                paraformer.encoder = paths.paraformerEncoder.orEmpty()
                paraformer.decoder = paths.paraformerDecoder.orEmpty()

                zipformer2Ctc.model = paths.zipformer2CtcModel.orEmpty()
                neMoCtc.model = paths.neMoCtcModel.orEmpty()
                toneCtc.model = paths.toneCtcModel.orEmpty()
            }

            val config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = options.sampleRate,
                    featureDim = options.featureDim,
                ),
                modelConfig = modelConfig,
                enableEndpoint = options.enableEndpoint,
                decodingMethod = options.decodingMethod,
                maxActivePaths = options.maxActivePaths,
            )

            val recognizer = OnlineRecognizer(context.assets, config)
            return SherpaOnnxAsr(recognizer)
        }
    }
}

