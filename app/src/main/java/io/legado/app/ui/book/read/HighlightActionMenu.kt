package io.legado.app.ui.book.read

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import io.legado.app.databinding.PopupHighlightActionBinding

/**
 * 点击已有高亮时弹出的操作菜单: 样式 / 笔记 / 规则 / 复制 / 删除
 */
class HighlightActionMenu(context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupHighlightActionBinding.inflate(LayoutInflater.from(context))

    init {
        contentView = binding.root
        isTouchable = true
        isOutsideTouchable = true
        isFocusable = false
        binding.tvStyle.setOnClickListener { callBack.onHighlightStyle() }
        binding.tvNote.setOnClickListener { callBack.onHighlightNote(); dismiss() }
        binding.tvBatch.setOnClickListener { callBack.onHighlightBatch(); dismiss() }
        binding.tvCopy.setOnClickListener { callBack.onHighlightCopy(); dismiss() }
        binding.tvDelete.setOnClickListener { callBack.onHighlightDelete(); dismiss() }
    }

    fun show(anchor: View, x: Int, topY: Int) {
        contentView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val h = contentView.measuredHeight
        val y = if (topY > h + 100) topY - h else topY + 80
        showAtLocation(anchor, Gravity.TOP or Gravity.START, x.coerceAtLeast(0), y.coerceAtLeast(0))
    }

    interface CallBack {
        fun onHighlightStyle()
        fun onHighlightNote()
        fun onHighlightBatch()
        fun onHighlightCopy()
        fun onHighlightDelete()
    }

    companion object {
        const val HL_FILL = 8101
        const val HL_TEXT = 8102
        const val HL_UNDERLINE = 8103
        const val HL_STRIKE = 8104
        const val HL_BOX = 8105
        const val HL_EMPHASIS = 8106
    }
}
