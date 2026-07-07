package io.legado.app.help.motion

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

/**
 * 按压弹性缩放：DOWN 弹到 pressedScale,UP/CANCEL 弹回 1f。
 * 挂 OnTouchListener 但恒返回 false——点击/长按/涟漪语义零改动。
 * MotionTokens.enabled=false 时 attach 直接不挂(eink/减少动画零开销)。
 * SpringAnimation 每属性只建一只、animateToFinalPosition 重定向——
 * 快速连点不会堆叠僵尸实例(dynamicanimation 内部按对象身份登记,无去重)。
 * 应用面按总纲 §2 克制:高频组件与门面,不全局撒。
 */
object PressSpringEffect {

    @SuppressLint("ClickableViewAccessibility")
    fun attach(view: View, pressedScale: Float = 0.94f) {
        if (!MotionTokens.enabled) return
        val force = MotionTokens.spring(view.context, MotionTokens.Spring.SPATIAL_FAST) ?: return
        fun buildSpring(property: androidx.dynamicanimation.animation.DynamicAnimation.ViewProperty) =
            SpringAnimation(view, property).apply {
                spring = SpringForce(1f)
                    .setStiffness(force.stiffness)
                    .setDampingRatio(force.dampingRatio)
            }
        val springX = buildSpring(SpringAnimation.SCALE_X)
        val springY = buildSpring(SpringAnimation.SCALE_Y)
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    springX.animateToFinalPosition(pressedScale)
                    springY.animateToFinalPosition(pressedScale)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    springX.animateToFinalPosition(1f)
                    springY.animateToFinalPosition(1f)
                }
            }
            return@setOnTouchListener false
        }
    }
}
