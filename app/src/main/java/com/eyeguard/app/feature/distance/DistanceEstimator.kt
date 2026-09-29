package com.eyeguard.app.feature.distance

/**
 * 眼睛到屏幕距离的估算器（纯计算，无 Android 依赖，便于单元测试）。
 *
 * 估算原理（针孔相机模型 + 相似三角形）：
 * ```
 * distance = (realFaceWidthCm * focalLengthPx) / faceWidthPx
 * ```
 * 其中：
 * - [faceWidthPx] 来自 ML Kit 人脸 boundingBox 的宽度（图像像素）；
 * - [focalLengthPx] 由前置相机水平视场角 HFOV 换算：
 *   `focalLengthPx = (imageWidthPx / 2) / tan(HFOV / 2)`。
 *   多数手机前置 HFOV 约 60°–70°，可用 Camera2 的
 *   `SENSOR_INFO_PHYSICAL_SIZE + LENS_INFO_AVAILABLE_FOCAL_LENGTHS` 精确计算，
 *   本实现使用调用方传入的标定值（见 [DistanceSampler] 中的默认常量）。
 * - [realFaceWidthCm] 为成年人面部真实宽度先验，取 14cm（两颊宽度中位数）。
 *   儿童偏小（约 11–12cm），会引入系统性高估，属已知误差。
 *
 * 精度限制：
 * - ML Kit 的 boundingBox 分辨率受图像尺寸与检测器精度限制，近距离（< 20cm）
 *   时人脸常超出画面导致漏检；> 50cm 时框宽误差被放大，估算值漂移明显。
 * - 因此输出仅在 20–50cm 范围内视为可信，超出范围返回 null。
 * - 光线不足、侧脸、戴眼镜/口罩都会显著增大误差（可达 ±10cm）。
 * - 结论是"量级正确的提醒"，不是测量仪器。
 */
object DistanceEstimator {

    /** 输出可信范围（厘米）。 */
    const val MIN_RELIABLE_CM = 20.0
    const val MAX_RELIABLE_CM = 50.0

    /** 成年人面部真实宽度先验（厘米）。 */
    const val DEFAULT_FACE_WIDTH_CM = 14.0

    /**
     * 由图像宽度和水平视场角换算焦距（像素）。
     *
     * @param imageWidthPx 图像宽度（像素）
     * @param hfovDegrees 前置相机水平视场角（度），默认 67°
     */
    fun focalLengthPx(imageWidthPx: Int, hfovDegrees: Double = 67.0): Double {
        val halfFov = Math.toRadians(hfovDegrees) / 2.0
        return (imageWidthPx / 2.0) / kotlin.math.tan(halfFov)
    }

    /**
     * 根据人脸框宽度估算眼睛到屏幕的距离。
     *
     * @param faceWidthPx ML Kit 人脸 boundingBox 宽度（像素）
     * @param imageWidthPx 分析图像宽度（像素），用于换算焦距
     * @param hfovDegrees 前置相机水平视场角（度）
     * @param realFaceWidthCm 面部真实宽度先验（厘米），默认成年人 14cm
     * @return 估算距离（厘米）；不在 20–50cm 可信范围内时返回 null
     */
    fun estimateCm(
        faceWidthPx: Float,
        imageWidthPx: Int,
        hfovDegrees: Double = 67.0,
        realFaceWidthCm: Double = DEFAULT_FACE_WIDTH_CM
    ): Double? {
        if (faceWidthPx <= 0f || imageWidthPx <= 0) return null
        val distanceCm = realFaceWidthCm * focalLengthPx(imageWidthPx, hfovDegrees) / faceWidthPx
        return if (distanceCm in MIN_RELIABLE_CM..MAX_RELIABLE_CM) distanceCm else null
    }
}
