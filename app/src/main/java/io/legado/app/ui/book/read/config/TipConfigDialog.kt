package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.indices
import com.google.android.material.chip.Chip
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReaderInfoTemplateBinding
import io.legado.app.databinding.DialogTipConfigBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.config.ReaderInfoTemplate
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.dialog.M3ColorPickerDialog
import io.legado.app.utils.checkByIndex
import io.legado.app.utils.getIndexById
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class TipConfigDialog : BaseDialogFragment(R.layout.dialog_tip_config) {

    companion object {
        const val TIP_COLOR = "tipConfigTipColor"
        const val TIP_DIVIDER_COLOR = "tipConfigTipDividerColor"
    }

    private val binding by viewBinding(DialogTipConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initView()
        initEvent()
        initColorPickerListeners()
        observeEvent<String>(EventBus.TIP_COLOR) {
            upTvTipColor()
            upTvTipDividerColor()
        }
    }

    private fun initColorPickerListeners() {
        parentFragmentManager.setFragmentResultListener(TIP_COLOR, viewLifecycleOwner) { _, bundle ->
            val color = bundle.getInt(M3ColorPickerDialog.RESULT_COLOR)
            ReadTipConfig.tipColor = color
            postEvent(EventBus.TIP_COLOR, "")
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
        parentFragmentManager.setFragmentResultListener(TIP_DIVIDER_COLOR, viewLifecycleOwner) { _, bundle ->
            val color = bundle.getInt(M3ColorPickerDialog.RESULT_COLOR)
            ReadTipConfig.tipDividerColor = color
            postEvent(EventBus.TIP_COLOR, "")
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
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

        initTipValues()
        upTvTipColor()
        upTvTipDividerColor()
    }

    private fun initTipValues() = binding.run {
        ReadTipConfig.run {
            llHeaderLeft.constrainValueWidth()
            llHeaderMiddle.constrainValueWidth()
            llHeaderRight.constrainValueWidth()
            llFooterLeft.constrainValueWidth()
            llFooterMiddle.constrainValueWidth()
            llFooterRight.constrainValueWidth()
            llHeaderLeft.value = effectiveTemplate(tipHeaderLeftTemplate, tipHeaderLeft)
            llHeaderMiddle.value = effectiveTemplate(tipHeaderMiddleTemplate, tipHeaderMiddle)
            llHeaderRight.value = effectiveTemplate(tipHeaderRightTemplate, tipHeaderRight)
            llFooterLeft.value = effectiveTemplate(tipFooterLeftTemplate, tipFooterLeft)
            llFooterMiddle.value = effectiveTemplate(tipFooterMiddleTemplate, tipFooterMiddle)
            llFooterRight.value = effectiveTemplate(tipFooterRightTemplate, tipFooterRight)
        }
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
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipHeaderLeftTemplate, tipHeaderLeft),
                ) { tipHeaderLeftTemplate = it }
            }
        }
        llHeaderMiddle.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipHeaderMiddleTemplate, tipHeaderMiddle),
                ) { tipHeaderMiddleTemplate = it }
            }
        }
        llHeaderRight.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipHeaderRightTemplate, tipHeaderRight),
                ) { tipHeaderRightTemplate = it }
            }
        }
        llFooterLeft.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipFooterLeftTemplate, tipFooterLeft),
                ) { tipFooterLeftTemplate = it }
            }
        }
        llFooterMiddle.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipFooterMiddleTemplate, tipFooterMiddle),
                ) { tipFooterMiddleTemplate = it }
            }
        }
        llFooterRight.setOnClickListener {
            ReadTipConfig.run {
                editTemplate(
                    title = getString(R.string.reader_info_template),
                    current = effectiveTemplate(tipFooterRightTemplate, tipFooterRight),
                ) { tipFooterRightTemplate = it }
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

                    1 -> M3ColorPickerDialog.show(
                        parentFragmentManager,
                        TIP_COLOR,
                        ReadTipConfig.tipColor,
                        false,
                        null
                    )
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

                    2 -> M3ColorPickerDialog.show(
                        parentFragmentManager,
                        TIP_DIVIDER_COLOR,
                        ReadTipConfig.tipDividerColor,
                        false,
                        null
                    )
                }
            }
        }
    }

    private fun editTemplate(
        title: String,
        current: String,
        save: (String) -> Unit,
    ) {
        val dialogBinding = DialogReaderInfoTemplateBinding.inflate(layoutInflater)
        dialogBinding.editTemplate.setText(current)
        dialogBinding.editTemplate.setSelection(current.length)
        ReaderInfoTemplate.placeholders.forEach { placeholder ->
            val chip = Chip(requireContext()).apply {
                text = placeholder
                isCheckable = false
                setOnClickListener {
                    val edit = dialogBinding.editTemplate
                    val editable = edit.editableText
                    val start = minOf(edit.selectionStart, edit.selectionEnd)
                        .coerceIn(0, editable.length)
                    val end = maxOf(edit.selectionStart, edit.selectionEnd)
                        .coerceIn(0, editable.length)
                    editable.replace(start, end, placeholder)
                }
            }
            dialogBinding.chipPlaceholders.addView(chip)
        }
        alert(title) {
            customView { dialogBinding.root }
            okButton {
                save(dialogBinding.editTemplate.editableText.toString())
                initTipValues()
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
            }
            cancelButton()
        }
    }

}
