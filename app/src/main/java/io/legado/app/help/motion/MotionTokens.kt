package io.legado.app.help.motion

import android.animation.ValueAnimator
import android.content.Context
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.R as MaterialR
import com.google.android.material.motion.MotionUtils
import io.legado.app.help.config.AppConfig

/**
 * 动效单门 + 弹簧取值器（总纲 §2）。
 * enabled=false（eink 或系统关动画）时所有取值器返回 null，调用方走无动效路径——
 * 关闭态行为必须与 N1 终态逐像素一致。
 * 弹簧参数解析自 material 主题 attr，附库默认 style 兜底（Theme.Material3 未挂时仍可用）。
 */
object MotionTokens {

    val enabled: Boolean
        get() = !AppConfig.isEInkMode && ValueAnimator.areAnimatorsEnabled()

    enum class Spring(val attr: Int, val defStyle: Int) {
        SPATIAL_DEFAULT(
            MaterialR.attr.motionSpringDefaultSpatial,
            MaterialR.style.Motion_Material3_Spring_Standard_Default_Spatial,
        ),
        SPATIAL_FAST(
            MaterialR.attr.motionSpringFastSpatial,
            MaterialR.style.Motion_Material3_Spring_Standard_Fast_Spatial,
        ),
        SPATIAL_SLOW(
            MaterialR.attr.motionSpringSlowSpatial,
            MaterialR.style.Motion_Material3_Spring_Standard_Slow_Spatial,
        ),
        EFFECTS_DEFAULT(
            MaterialR.attr.motionSpringDefaultEffects,
            MaterialR.style.Motion_Material3_Spring_Standard_Default_Effects,
        ),
        EFFECTS_FAST(
            MaterialR.attr.motionSpringFastEffects,
            MaterialR.style.Motion_Material3_Spring_Standard_Fast_Effects,
        ),
        EFFECTS_SLOW(
            MaterialR.attr.motionSpringSlowEffects,
            MaterialR.style.Motion_Material3_Spring_Standard_Slow_Effects,
        ),
    }

    fun spring(context: Context, token: Spring): SpringForce? {
        if (!enabled) return null
        return MotionUtils.resolveThemeSpringForce(context, token.attr, token.defStyle)
    }
}
