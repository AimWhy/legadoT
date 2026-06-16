package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.indices
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getIndexById
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding


class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {

    companion object {
        const val TIP_COLOR = 7897
        const val TIP_DIVIDER_COLOR = 7898
    }

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
        observeEvent<String>(EventBus.TIP_COLOR) {
            upTvTipColor()
            upTvTipDividerColor()
        }
    }

    private fun initView() {
        if (ReadBookConfig.titleMode !in binding.rgTitleMode.indices) {
            ReadBookConfig.titleMode = 0
        }
        binding.rgTitleMode.checkByIndex(ReadBookConfig.titleMode)
        binding.dsbTitleSize.progress = ReadBookConfig.titleSize
        binding.dsbTitleTop.progress = ReadBookConfig.titleTopSpacing
        binding.dsbTitleBottom.progress = ReadBookConfig.titleBottomSpacing

        binding.llHeaderShow.value =
            ReadTipConfig.getHeaderModes(requireContext())[ReadTipConfig.headerMode]
        binding.llFooterShow.value =
            ReadTipConfig.getFooterModes(requireContext())[ReadTipConfig.footerMode]

        ReadTipConfig.run {
            tipNames.let { tipNames ->
                binding.llHeaderLeft.value =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderLeft)) { tipNames[none] }
                binding.llHeaderMiddle.value =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderMiddle)) { tipNames[none] }
                binding.llHeaderRight.value =
                    tipNames.getOrElse(tipValues.indexOf(tipHeaderRight)) { tipNames[none] }
                binding.llFooterLeft.value =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterLeft)) { tipNames[none] }
                binding.llFooterMiddle.value =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterMiddle)) { tipNames[none] }
                binding.llFooterRight.value =
                    tipNames.getOrElse(tipValues.indexOf(tipFooterRight)) { tipNames[none] }
            }
        }
        upTvTipColor()
        upTvTipDividerColor()
    }

    private fun upTvTipColor() {
        val tipColorNames = ReadTipConfig.tipColorNames
        val tipColor = ReadTipConfig.tipColor
        binding.llTipColor.value = if (tipColor == 0) {
            tipColorNames.first()
        } else {
            "#${tipColor.hexString}"
        }
    }

    private fun upTvTipDividerColor() {
        val tipDividerColorNames = ReadTipConfig.tipDividerColorNames
        val tipDividerColor = ReadTipConfig.tipDividerColor
        binding.llTipDividerColor.value = when (tipDividerColor) {
            -1, 0 -> tipDividerColorNames[tipDividerColor + 1]
            else -> "#${tipDividerColor.hexString}"
        }
    }

    private fun initEvent() = binding.run {
        rgTitleMode.setOnCheckedChangeListener { _, checkedId ->
            ReadBookConfig.titleMode = rgTitleMode.getIndexById(checkedId)
            postEvent(EventBus.UP_CONFIG, arrayListOf(5))
        }
        dsbTitleSize.onChanged = {
            ReadBookConfig.titleSize = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleTop.onChanged = {
            ReadBookConfig.titleTopSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        dsbTitleBottom.onChanged = {
            ReadBookConfig.titleBottomSpacing = it
            postEvent(EventBus.UP_CONFIG, arrayListOf(8, 5))
        }
        llHeaderShow.setOnClickListener {
            val headerModes = ReadTipConfig.getHeaderModes(requireContext())
            context?.selector(items = headerModes.values.toList()) { _, i ->
                ReadTipConfig.headerMode = headerModes.keys.toList()[i]
                llHeaderShow.value = headerModes[ReadTipConfig.headerMode]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        llFooterShow.setOnClickListener {
            val footerModes = ReadTipConfig.getFooterModes(requireContext())
            context?.selector(items = footerModes.values.toList()) { _, i ->
                ReadTipConfig.footerMode = footerModes.keys.toList()[i]
                llFooterShow.value = footerModes[ReadTipConfig.footerMode]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
        llHeaderLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderLeft = tipValue
                llHeaderLeft.value = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llHeaderMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderMiddle = tipValue
                llHeaderMiddle.value = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llHeaderRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipHeaderRight = tipValue
                llHeaderRight.value = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterLeft.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterLeft = tipValue
                llFooterLeft.value = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterMiddle.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterMiddle = tipValue
                llFooterMiddle.value = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llFooterRight.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipNames) { _, i ->
                val tipValue = ReadTipConfig.tipValues[i]
                clearRepeat(tipValue)
                ReadTipConfig.tipFooterRight = tipValue
                llFooterRight.value = ReadTipConfig.tipNames[i]
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
        }
        llTipColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipColorNames) { _, i ->
                when (i) {
                    0 -> {
                        ReadTipConfig.tipColor = 0
                        upTvTipColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    1 -> ColorPickerDialog.newBuilder()
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_COLOR)
                        .show(requireActivity())
                }
            }
        }
        llTipDividerColor.setOnClickListener {
            context?.selector(items = ReadTipConfig.tipDividerColorNames) { _, i ->
                when (i) {
                    0, 1 -> {
                        ReadTipConfig.tipDividerColor = i - 1
                        upTvTipDividerColor()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    }

                    2 -> ColorPickerDialog.newBuilder()
                        .setShowAlphaSlider(false)
                        .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                        .setDialogId(TIP_DIVIDER_COLOR)
                        .show(requireActivity())
                }
            }
        }
    }

    private fun clearRepeat(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) {
                tipHeaderLeft = none
                binding.llHeaderLeft.value = tipNames[none]
            }
            if (tipHeaderMiddle == repeat) {
                tipHeaderMiddle = none
                binding.llHeaderMiddle.value = tipNames[none]
            }
            if (tipHeaderRight == repeat) {
                tipHeaderRight = none
                binding.llHeaderRight.value = tipNames[none]
            }
            if (tipFooterLeft == repeat) {
                tipFooterLeft = none
                binding.llFooterLeft.value = tipNames[none]
            }
            if (tipFooterMiddle == repeat) {
                tipFooterMiddle = none
                binding.llFooterMiddle.value = tipNames[none]
            }
            if (tipFooterRight == repeat) {
                tipFooterRight = none
                binding.llFooterRight.value = tipNames[none]
            }
        }
    }

}