package io.legado.app.ui.highlight.edit

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookHighlight
import io.legado.app.data.entities.HighlightRule
import io.legado.app.databinding.DialogHighlightRuleEditBinding
import io.legado.app.help.HighlightColors
import io.legado.app.help.HighlightStyle
import io.legado.app.lib.theme.primaryColor
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.HighlightStyleDialog
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编辑高亮规则(全屏对话框,与书签备注同视觉风格)。
 * 作为 [HighlightStyleDialog] 的 [HighlightStyleDialog.StyleHost]。
 */
class HighlightRuleEditDialog : BaseDialogFragment(R.layout.dialog_highlight_rule_edit, true),
    HighlightStyleDialog.StyleHost,
    ColorPickerDialogListener {

    companion object {
        private const val COLOR_PICKER_TAG = "highlight-rule-color-picker"

        /**
         * 新建规则(预填 pattern/isRegex/scope/style)。
         * [sourceHighlightTime] > 0 表示由「批量」从某手动划线发起,保存成功后删除该划线(转化为规则);
         * 0 表示规则管理页直接新增,不影响任何划线。
         */
        fun create(
            pattern: String,
            isRegex: Boolean = false,
            scope: String? = null,
            style: String? = null,
            sourceHighlightTime: Long = 0L
        ): HighlightRuleEditDialog = HighlightRuleEditDialog().apply {
            arguments = Bundle().apply {
                putString("pattern", pattern)
                putBoolean("isRegex", isRegex)
                putString("scope", scope)
                putString("style", style)
                putLong("sourceHighlightTime", sourceHighlightTime)
            }
        }

        /** 编辑已有规则 */
        fun edit(id: Long): HighlightRuleEditDialog = HighlightRuleEditDialog().apply {
            arguments = Bundle().apply { putLong("id", id) }
        }

        /** 「批量」保存成功后应删除的来源划线; sourceTime<=0(规则管理页新增)时返回 null */
        fun highlightToRemove(highlights: List<BookHighlight>, sourceTime: Long): BookHighlight? =
            if (sourceTime > 0) highlights.firstOrNull { it.time == sourceTime } else null

        data class ColorPickerConfig(
            val dialogId: Int,
            val color: Int,
            val withAlpha: Boolean,
            val presets: IntArray
        )

        fun colorPickerConfig(dialogId: Int, initial: Int, withAlpha: Boolean): ColorPickerConfig {
            val seed = if (initial != 0) initial else HighlightColors.bg.first()
            return ColorPickerConfig(
                dialogId = dialogId,
                color = seed,
                withAlpha = withAlpha,
                presets = if (withAlpha) HighlightColors.bg else HighlightColors.text
            )
        }

        fun bindColorPickerListener(
            dialog: ColorPickerDialog,
            listener: ColorPickerDialogListener
        ): ColorPickerDialog = dialog.also { it.setColorPickerDialogListener(listener) }

        fun createColorPickerDialog(
            dialogId: Int,
            initial: Int,
            withAlpha: Boolean,
            listener: ColorPickerDialogListener
        ): ColorPickerDialog {
            val config = colorPickerConfig(dialogId, initial, withAlpha)
            return bindColorPickerListener(
                ColorPickerDialog.newBuilder()
                    .setColor(config.color)
                    .setShowAlphaSlider(config.withAlpha)
                    .setDialogType(ColorPickerDialog.TYPE_PRESETS)
                    .setPresets(config.presets)
                    .setDialogId(config.dialogId)
                    .create(),
                listener
            )
        }
    }

    private val binding by viewBinding(DialogHighlightRuleEditBinding::bind)
    private var editingStyle = HighlightStyle()
    private var styleDialog: HighlightStyleDialog? = null
    private var rule: HighlightRule? = null

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.btnStyle.setOnClickListener {
            val d = HighlightStyleDialog()
            styleDialog = d
            showDialogFragment(d)
        }
        binding.tvCancel.setOnClickListener { dismiss() }
        binding.tvOk.setOnClickListener { save() }
        childFragmentManager.findFragmentByTag(COLOR_PICKER_TAG)
            ?.let { it as? ColorPickerDialog }
            ?.setColorPickerDialogListener(this)

        val id = arguments?.getLong("id", -1) ?: -1
        if (id > 0) {
            loadById(id)
        } else {
            fromArgs()
        }
    }

    private fun loadById(id: Long) {
        lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) { appDb.highlightRuleDao.findById(id) }
            if (r != null) {
                rule = r
                upView(r)
            } else {
                requireActivity().toastOnUi("规则不存在")
                dismiss()
            }
        }
    }

    private fun fromArgs() {
        val a = arguments ?: return
        val r = HighlightRule(
            name = a.getString("pattern") ?: "",
            pattern = a.getString("pattern") ?: "",
            isRegex = a.getBoolean("isRegex", false),
            scope = a.getString("scope"),
            style = a.getString("style") ?: ""
        )
        rule = r
        upView(r)
    }

    private fun upView(r: HighlightRule) = binding.run {
        etName.setText(r.name)
        etPattern.setText(r.pattern)
        cbUseRegex.isChecked = r.isRegex
        etScope.setText(r.scope)
        editingStyle = r.styleObj()
        upPreview()
    }

    private fun getRule(): HighlightRule = binding.run {
        val r = rule ?: HighlightRule()
        r.name = etName.text.toString()
        r.pattern = etPattern.text.toString()
        r.isRegex = cbUseRegex.isChecked
        r.scope = etScope.text.toString().ifBlank { null }
        r.applyStyle(editingStyle)
        r
    }

    private fun save() {
        val r = getRule()
        if (!r.isValid()) {
            requireActivity().toastOnUi("规则无效: ${r.pattern}")
            return
        }
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (r.order == Int.MIN_VALUE) {
                    r.order = appDb.highlightRuleDao.maxOrder + 1
                }
                appDb.highlightRuleDao.insert(r)
            }
            ReadBook.upHighlightRules()
            // 「批量」转化: 规则已接管该处文字, 删除发起的那条手动划线, 避免同段文字双份高亮
            val srcTime = arguments?.getLong("sourceHighlightTime", 0L) ?: 0L
            highlightToRemove(ReadBook.highlights, srcTime)?.let { ReadBook.removeHighlight(it) }
            dismiss()
        }
    }

    private fun upPreview() = binding.run {
        tvStylePreview.setBackgroundColor(editingStyle.fill)
        if (editingStyle.textColor != 0) tvStylePreview.setTextColor(editingStyle.textColor)
    }

    // --- HighlightStyleDialog.StyleHost ---
    override fun currentHighlightStyle(): HighlightStyle = editingStyle

    override fun onHighlightStyleChanged(style: HighlightStyle) {
        editingStyle = style
        upPreview()
    }

    override fun pickHighlightColor(dialogId: Int, initial: Int, withAlpha: Boolean) {
        createColorPickerDialog(
            dialogId = dialogId,
            initial = initial,
            withAlpha = withAlpha,
            listener = this@HighlightRuleEditDialog
        ).show(childFragmentManager, COLOR_PICKER_TAG)
    }

    // --- ColorPickerDialogListener ---
    override fun onColorSelected(dialogId: Int, color: Int) {
        editingStyle = HighlightStyleDialog.applyChannelColor(editingStyle, dialogId, color)
        styleDialog?.refresh()
        upPreview()
    }

    override fun onDialogDismissed(dialogId: Int) {}
}
