package io.legado.app.help.motion

import android.content.Context
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.shape.MaterialShapeDrawable

/**
 * 圆角形变弹簧(Expressive 按压 morph 的自研后端——1.13 无 cornerSpringForce,AAR 实证)。
 * N2 交付机制,N3 门面(音频播放键等)消费;enabled=false 返回 null,调用方直接 setCornerSize(to)。
 * 连续变形勿反复调用本方法——复用返回的 SpringAnimation 调 animateToFinalPosition(newTarget)。
 * 返回实例可 cancel()。
 */
object ShapeMorph {

    fun animateCornerSize(
        drawable: MaterialShapeDrawable,
        from: Float,
        to: Float,
        context: Context,
    ): SpringAnimation? {
        val force = MotionTokens.spring(context, MotionTokens.Spring.SPATIAL_FAST) ?: return null
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = SpringForce(to)
                .setStiffness(force.stiffness)
                .setDampingRatio(force.dampingRatio)
            setMinValue(0f)
            addUpdateListener { _, value, _ -> drawable.setCornerSize(value) }
            start()
        }
    }
}
