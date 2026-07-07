package io.legado.app.ui.book.audio

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import io.legado.app.R
import io.legado.app.databinding.PopupSeekBarBinding
import io.legado.app.model.AudioPlay
import io.legado.app.service.AudioPlayService
import io.legado.app.utils.applyMd3PopupStyle

class TimerSliderPopup(private val context: Context) :
    PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupSeekBarBinding.inflate(LayoutInflater.from(context))

    init {
        contentView = binding.root
        applyMd3PopupStyle()

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = true

        binding.seekBar.valueTo = 180f
        setProcessTextValue(binding.seekBar.value.toInt())
        binding.seekBar.addOnChangeListener { _, value, fromUser ->
            setProcessTextValue(value.toInt())
            if (fromUser) {
                AudioPlay.setTimer(value.toInt())
            }
        }
    }

    override fun showAsDropDown(anchor: View?, xoff: Int, yoff: Int, gravity: Int) {
        super.showAsDropDown(anchor, xoff, yoff, gravity)
        binding.seekBar.value = AudioPlayService.timeMinute.toFloat()
            .coerceIn(binding.seekBar.valueFrom, binding.seekBar.valueTo)
    }

    override fun showAtLocation(parent: View?, gravity: Int, x: Int, y: Int) {
        super.showAtLocation(parent, gravity, x, y)
        binding.seekBar.value = AudioPlayService.timeMinute.toFloat()
            .coerceIn(binding.seekBar.valueFrom, binding.seekBar.valueTo)
    }

    private fun setProcessTextValue(process: Int) {
        binding.tvSeekValue.text = context.getString(R.string.timer_m, process)
    }

}