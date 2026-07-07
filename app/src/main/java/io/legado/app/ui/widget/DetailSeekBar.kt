package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.widget.TooltipCompat
import com.google.android.material.slider.Slider
import io.legado.app.R
import io.legado.app.databinding.ViewDetailSeekBarBinding
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyAppTint


class DetailSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    private var binding: ViewDetailSeekBarBinding =
        ViewDetailSeekBarBinding.inflate(LayoutInflater.from(context), this, true)
    private val isBottomBackground: Boolean

    var valueFormat: ((progress: Int) -> String)? = null
    var onChanged: ((progress: Int) -> Unit)? = null
    var progress: Int
        get() = binding.seekBar.value.toInt()
        set(value) {
            binding.seekBar.value =
                value.toFloat().coerceIn(binding.seekBar.valueFrom, binding.seekBar.valueTo)
            upValue()
        }
    var max: Int
        get() = binding.seekBar.valueTo.toInt()
        set(value) {
            binding.seekBar.valueTo = value.coerceAtLeast(1).toFloat()
            binding.seekBar.value = binding.seekBar.value.coerceAtMost(binding.seekBar.valueTo)
        }

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.DetailSeekBar)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.DetailSeekBar_isBottomBackground, false)
        val title = typedArray.getText(R.styleable.DetailSeekBar_title)
        binding.tvSeekTitle.apply {
            text = title
            TooltipCompat.setTooltipText(this, title)
        }
        max = typedArray.getInteger(R.styleable.DetailSeekBar_max, 0)
        typedArray.recycle()
        if (isBottomBackground && !isInEditMode) {
            val isLight = ColorUtils.isColorLight(context.bottomBackground)
            val textColor = context.getPrimaryTextColor(isLight)
            binding.tvSeekTitle.setTextColor(textColor)
            binding.ivSeekPlus.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
            binding.ivSeekReduce.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
            binding.tvSeekValue.setTextColor(textColor)
            binding.seekBar.applyAppTint(textColor)
        }
        binding.ivSeekPlus.setOnClickListener {
            binding.seekBar.value = (binding.seekBar.value + 1)
                .coerceIn(binding.seekBar.valueFrom, binding.seekBar.valueTo)
            onChanged?.invoke(binding.seekBar.value.toInt())
        }
        binding.ivSeekReduce.setOnClickListener {
            binding.seekBar.value = (binding.seekBar.value - 1)
                .coerceIn(binding.seekBar.valueFrom, binding.seekBar.valueTo)
            onChanged?.invoke(binding.seekBar.value.toInt())
        }
        binding.seekBar.addOnChangeListener { _, value, _ -> upValue(value.toInt()) }
        binding.seekBar.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) = Unit
            override fun onStopTrackingTouch(slider: Slider) {
                onChanged?.invoke(slider.value.toInt())
            }
        })
    }

    private fun upValue(progress: Int = binding.seekBar.value.toInt()) {
        valueFormat?.let {
            binding.tvSeekValue.text = it.invoke(progress)
        } ?: let {
            binding.tvSeekValue.text = progress.toString()
        }
    }

}
