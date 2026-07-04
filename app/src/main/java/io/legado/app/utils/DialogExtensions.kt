package io.legado.app.utils

import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.forEach
import androidx.fragment.app.DialogFragment
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.filletBackground
import splitties.systemservices.windowManager

fun AlertDialog.applyTint(): AlertDialog {
    window?.setBackgroundDrawable(context.filletBackground)
    val colorStateList = Selector.colorBuild()
        .setDefaultColor(context.accentColor)
        .setPressedColor(ColorUtils.darkenColor(context.accentColor))
        .create()
    if (getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
        getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_POSITIVE) != null) {
        getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(colorStateList)
    }
    if (getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
        getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(colorStateList)
    }
    window?.decorView?.post {
        listView?.forEach {
            it.applyTint(context.accentColor)
        }
    }
    return this
}

fun AlertDialog.requestInputMethod() {
    window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
}

/**
 * 统一 BottomSheet 容器外观:顶部 radius_xl 圆角 + surfaceContainerLow(M3 sheet 容器色,用户背景锚定,
 * attr 路径拿不到锚定色所以代码设置);eink 时为顶部边框方角。
 * 在 onStart/show 之后调用(design_bottom_sheet 已就位)。
 */
fun Dialog.applyAppSheetBackground() {
    val sheet =
        findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
    if (AppConfig.isEInkMode) {
        sheet.setBackgroundResource(R.drawable.bg_eink_border_top)
        return
    }
    val radius = context.resources.getDimension(R.dimen.radius_xl)
    sheet.background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
        setColor(AppColorScheme.current.surfaceContainerLow)
    }
    sheet.clipToOutline = true
}

fun DialogFragment.setLayout(widthMix: Float, heightMix: Float) {
    dialog?.setLayout(widthMix, heightMix)
}

fun Dialog.setLayout(widthMix: Float, heightMix: Float) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        (dm.heightPixels * heightMix).toInt()
    )
}

fun DialogFragment.setLayout(width: Int, heightMix: Float) {
    dialog?.setLayout(width, heightMix)
}

fun Dialog.setLayout(width: Int, heightMix: Float) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        width,
        (dm.heightPixels * heightMix).toInt()
    )
}

fun DialogFragment.setLayout(widthMix: Float, height: Int) {
    dialog?.setLayout(widthMix, height)
}

fun Dialog.setLayout(widthMix: Float, height: Int) {
    val dm = context.windowManager.windowSize
    window?.setLayout(
        (dm.widthPixels * widthMix).toInt(),
        height
    )
}

fun DialogFragment.setLayout(width: Int, height: Int) {
    dialog?.setLayout(width, height)
}

fun Dialog.setLayout(width: Int, height: Int) {
    window?.setLayout(width, height)
}