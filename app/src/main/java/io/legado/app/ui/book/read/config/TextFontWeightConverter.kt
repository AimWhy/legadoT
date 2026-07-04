package io.legado.app.ui.book.read.config

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.applyBottomPanelStrokeStyle


/**
 * 字重切换指示器(细/中/粗):激活档用 accent 字符 span 标示,
 * 描边外观与阅读面板 MaterialButton 描边钮对齐(bottomBackground 亮暗感知)。
 */
class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    AppCompatTextView(context, attrs) {

    private val spannableString = SpannableString(context.getString(R.string.font_weight_text))
    private var enabledSpan: ForegroundColorSpan = ForegroundColorSpan(context.accentColor)
    private var onChanged: (() -> Unit)? = null

    init {
        text = spannableString
        if (!isInEditMode) {
            applyBottomPanelStrokeStyle()
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            selectType()
        }
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun upUi(type: Int) {
        spannableString.removeSpan(enabledSpan)
        when (type) {
            0 -> spannableString.setSpan(enabledSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            1 -> spannableString.setSpan(enabledSpan, 2, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            2 -> spannableString.setSpan(enabledSpan, 4, 5, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        text = spannableString
    }

    private fun selectType() {
        context.alert(titleResource = R.string.text_font_weight_converter) {
            items(context.resources.getStringArray(R.array.text_font_weight).toList()) { _, i ->
                ReadBookConfig.textBold = i
                upUi(i)
                onChanged?.invoke()
            }
        }
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}