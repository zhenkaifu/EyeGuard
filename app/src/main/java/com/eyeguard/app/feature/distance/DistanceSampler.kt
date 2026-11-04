package com.eyeguard.app.feature.distance

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 一次性距离采样器：每次采样临时绑定前置相机分析一帧，完成后立即解绑释放相机，
 * 严禁相机长开（省电 + 避免隐私顾虑 + 避免系统认为相机被占用）。
 *
 * 线程模型：CameraX 绑定 / 解绑在主线程；图像分析与 ML Kit 检测在专用后台线程
 * （每调用创建并 shutdown 的单线程 executor，不泄漏）；主线程只做 latch 等待与绑定操作，
 * 不做重计算。
 *
 * 返回 [SampleResult]：估算距离（不可信/未检测到人脸时为 null）与原始框宽，供命中判定使用。
 */
@Singleton
class DistanceSampler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sampleMutex = Mutex()

    sealed class SampleResult {
        /** 采样成功；[distanceCm] 为可信估算值，null 表示未检测到人脸或超出可信范围。 */
        data class Success(val distanceCm: Double?, val faceWidthPx: Float?) : SampleResult()
        /** 相机不可用（被占用 / 权限缺失 / 超时等），跳过本次采样。 */
        data object Unavailable : SampleResult()
    }

    /**
     * 执行一次采样（挂起，约 1–3 秒）。
     *
     * 只绑定 [ImageAnalysis] 用例（无需 Preview 也能出帧）；取到第一帧后同步执行
     * ML Kit 人脸检测并关闭 [ImageProxy]，随后解绑全部用例、关闭检测器与 executor。
     *
     * @param lifecycleOwner 宿主生命周期（服务自身实现 [LifecycleOwner]）
     */
    suspend fun sampleOnce(lifecycleOwner: LifecycleOwner): SampleResult =
        sampleMutex.withLock {
            withContext(Dispatchers.Main) {
            val provider = runCatching { awaitProvider() }.getOrElse {
                return@withContext SampleResult.Unavailable
            }

            val frameLatch = CountDownLatch(1)
            var sampleResult: SampleResult = SampleResult.Unavailable
            var analyzed = false

            // 每次采样独立 executor，结束后 shutdown，避免线程泄漏
            val analysisExecutor = Executors.newSingleThreadExecutor()

            val detector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setMinFaceSize(0.15f)
                    .build()
            )

            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(
                            Size(640, 480),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                // 只分析第一帧，防止长开
                if (analyzed) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                analyzed = true
                sampleResult = runCatching { detectDistance(detector, imageProxy) }
                    .getOrElse { SampleResult.Unavailable }
                frameLatch.countDown()
                imageProxy.close()
            }

            try {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    analysis
                )
            } catch (e: Exception) {
                // 相机被占用 / 权限缺失
                cleanup(analysisExecutor, detector)
                return@withContext SampleResult.Unavailable
            }

            // 主线程不阻塞等待：切到后台线程等首帧（上限 3 秒）
            withContext(Dispatchers.Default) {
                runCatching { frameLatch.await(3, TimeUnit.SECONDS) }
            }

            // 立即释放相机资源
            runCatching { provider.unbindAll() }
                cleanup(analysisExecutor, detector)
                sampleResult
            }
        }

    private suspend fun awaitProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching { cont.resume(future.get()) }
                .onFailure { cont.resumeWithException(it) }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun cleanup(executor: java.util.concurrent.ExecutorService, detector: com.google.mlkit.vision.face.FaceDetector) {
        runCatching { executor.shutdown() }
        runCatching { detector.close() }
    }

    /** 对单帧执行人脸检测并估算距离（运行在 analysisExecutor 后台线程）。 */
    private fun detectDistance(
        detector: com.google.mlkit.vision.face.FaceDetector,
        imageProxy: ImageProxy
    ): SampleResult {
        val media = imageProxy.image ?: return SampleResult.Unavailable
        val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
        val faces = com.google.android.gms.tasks.Tasks.await(detector.process(input))
        val face = faces.maxByOrNull { it.boundingBox.width() }
            ?: return SampleResult.Success(null, null)
        val faceWidth = face.boundingBox.width().toFloat()
        val distanceCm = DistanceEstimator.estimateCm(
            faceWidthPx = faceWidth,
            imageWidthPx = imageProxy.width
        )
        return SampleResult.Success(distanceCm, faceWidth)
    }
}
