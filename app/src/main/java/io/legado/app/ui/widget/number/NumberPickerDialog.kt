package io.legado.app.ui.widget.number

import android.content.Context
import android.widget.NumberPicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.applyTint
import io.legado.app.utils.hideSoftInput


class NumberPickerDialog(context: Context) {
    // MaterialAlertDialogBuilder:与 alert DSL 同步的 M3 容器(28dp/surfaceContainerHigh),applyTint 兜底染按钮
    private val builder = MaterialAlertDialogBuilder(context)
    private var numberPicker: NumberPicker? = null
    private var maxValue: Int? = null
    private var minValue: Int? = null
    private var value: Int? = null

    init {
        builder.setView(R.layout.dialog_number_picker)
    }

    fun setTitle(title: String): NumberPickerDialog {
        builder.setTitle(title)
        return this
    }

    fun setMaxValue(value: Int): NumberPickerDialog {
        maxValue = value
        return this
    }

    fun setMinValue(value: Int): NumberPickerDialog {
        minValue = value
        return this
    }

    fun setValue(value: Int): NumberPickerDialog {
        this.value = value
        return this
    }

    fun setCustomButton(textId: Int, listener: (() -> Unit)?): NumberPickerDialog {
        builder.setNeutralButton(textId) { _, _ ->
            numberPicker?.let {
                it.clearFocus()
                it.hideSoftInput()
                listener?.invoke()
            }
        }
        return this
    }

    fun show(callBack: ((value: Int) -> Unit)?) {
        builder.setPositiveButton(R.string.ok) { _, _ ->
            numberPicker?.let {
                it.clearFocus()
                it.hideSoftInput()
                callBack?.invoke(it.value)
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        val dialog = builder.show().applyTint()
        if (AppConfig.isEInkMode) {
            // 与 AndroidAlertBuilder.show() 的 eink 分支同款(此类不走 alert DSL,自行补齐)
            dialog.window?.run {
                val attr = attributes
                attr.dimAmount = 0f
                attr.windowAnimations = 0
                attributes = attr
                setBackgroundDrawableResource(R.drawable.bg_eink_border_dialog)
            }
        }
        numberPicker = dialog.findViewById(R.id.number_picker)
        numberPicker?.let { np ->
            minValue?.let {
                np.minValue = it
            }
            maxValue?.let {
                np.maxValue = it
            }
            value?.let {
                np.value = it
            }
        }
    }
}