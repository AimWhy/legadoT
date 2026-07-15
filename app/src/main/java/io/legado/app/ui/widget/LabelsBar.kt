package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.children
import io.legado.app.R
import io.legado.app.ui.widget.text.AccentBgTextView
import io.legado.app.utils.dpToPx
import kotlin.math.max
import kotlin.math.min

/**
 * 标签流式栏:一行放不下时自动换行,行数由 labelsMaxRows 封顶(默认 1,0=不限)。
 * 高度受限时(如 layout_constrainedHeight)按整行取齐:放不下的行整行隐藏,
 * 不出现半截行,首行永远保底;android:gravity 含水平居中时逐行居中排布。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class LabelsBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ViewGroup(context, attrs) {

    private val unUsedViews = arrayListOf<TextView>()
    private val usedViews = arrayListOf<TextView>()
    var textSize = 12f
    var maxRows: Int
    private val centerRows: Boolean
    private val rowSpacing = 4.dpToPx()

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.LabelsBar)
        maxRows = ta.getInt(R.styleable.LabelsBar_labelsMaxRows, 1)
        ta.recycle()
        val ga = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.gravity))
        centerRows = ga.getInt(0, Gravity.NO_GRAVITY) and
                Gravity.CENTER_HORIZONTAL == Gravity.CENTER_HORIZONTAL
        ga.recycle()
    }

    fun setLabels(labels: Array<String>) {
        clear()
        labels.forEach {
            addLabel(it, null, null)
        }
    }

    fun setLabels(labels: List<String>) {
        clear()
        labels.forEach {
            addLabel(it, null, null)
        }
    }

    fun setLabels(
        labels: List<String>,
        onClick: ((String) -> Unit)?,
        onLongClick: ((String) -> Boolean)?
    ) {
        clear()
        labels.forEach {
            addLabel(it, onClick, onLongClick)
        }
    }

    fun clear() {
        unUsedViews.addAll(usedViews)
        usedViews.clear()
        removeAllViews()
    }

    fun addLabel(label: String, onClick: ((String) -> Unit)?, onLongClick: ((String) -> Boolean)?) {
        val tv = if (unUsedViews.isEmpty()) {
            AccentBgTextView(context, null).apply {
                setPadding(3.dpToPx(), 0, 3.dpToPx(), 0)
                setRadius(2)
                val lp = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 2.dpToPx(), 0)
                layoutParams = lp
                text = label
                maxLines = 1
                usedViews.add(this)
            }
        } else {
            unUsedViews.last().apply {
                usedViews.add(this)
                unUsedViews.removeAt(unUsedViews.lastIndex)
            }
        }
        tv.textSize = textSize
        tv.text = label
        if (onClick != null) {
            tv.setOnClickListener { onClick.invoke(label) }
        }
        if (onLongClick != null) {
            tv.setOnLongClickListener { onLongClick.invoke(label) }
        }
        addView(tv)
    }

    private class Row {
        val views = arrayListOf<View>()
        var width = 0
        var height = 0
    }

    private fun buildRows(availWidth: Int): ArrayList<Row> {
        val rows = arrayListOf<Row>()
        var cur: Row? = null
        children.forEach { child ->
            if (child.visibility == GONE) return@forEach
            val lp = child.layoutParams as MarginLayoutParams
            val cw = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = child.measuredHeight + lp.topMargin + lp.bottomMargin
            var row = cur
            if (row == null || row.width + cw > availWidth) {
                row = Row()
                rows.add(row)
                cur = row
            }
            row.views.add(child)
            row.width += cw
            row.height = max(row.height, ch)
        }
        return rows
    }

    private fun visibleRowCount(rows: List<Row>, heightBudget: Int): Int {
        val cap = if (maxRows > 0) min(rows.size, maxRows) else rows.size
        var used = 0
        var count = 0
        for (i in 0 until cap) {
            val add = rows[i].height + if (i > 0) rowSpacing else 0
            if (count > 0 && used + add > heightBudget) break
            used += add
            count++
        }
        return count
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        children.forEach { child ->
            if (child.visibility != GONE) {
                measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            }
        }
        val availWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val rows = buildRows(availWidth)
        val heightBudget = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            Int.MAX_VALUE
        } else {
            MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        }
        val count = visibleRowCount(rows, heightBudget)
        var contentWidth = 0
        var contentHeight = 0
        for (i in 0 until count) {
            contentWidth = max(contentWidth, rows[i].width)
            contentHeight += rows[i].height + if (i > 0) rowSpacing else 0
        }
        setMeasuredDimension(
            View.resolveSize(contentWidth + paddingLeft + paddingRight, widthMeasureSpec),
            View.resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val availWidth = r - l - paddingLeft - paddingRight
        val rows = buildRows(availWidth)
        val count = visibleRowCount(rows, b - t - paddingTop - paddingBottom)
        var y = paddingTop
        rows.forEachIndexed { index, row ->
            if (index >= count) {
                // 放不下的行整行藏掉(零尺寸,不可见不可点)
                row.views.forEach { it.layout(0, 0, 0, 0) }
                return@forEachIndexed
            }
            if (index > 0) y += rowSpacing
            var x = paddingLeft + if (centerRows) max(0, (availWidth - row.width) / 2) else 0
            row.views.forEach { child ->
                val lp = child.layoutParams as MarginLayoutParams
                x += lp.leftMargin
                val top = y + lp.topMargin
                child.layout(x, top, x + child.measuredWidth, top + child.measuredHeight)
                x += child.measuredWidth + lp.rightMargin
            }
            y += row.height
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams =
        MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams
}
