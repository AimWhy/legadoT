package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemTextSelectMenuConfigBinding
import io.legado.app.help.TextSelectMenuConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.read.TextSelectMenuItem
import io.legado.app.ui.book.read.loadTextSelectMenuConfig
import io.legado.app.ui.book.read.saveTextSelectMenuConfig
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.gone
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible

/**
 * 自定义文字选择浮动条:两区(浮动条/更多)拖拽排序、点箭头跨区移动。
 *
 * 列表用两条**常驻分隔行**(浮动条头、更多头)把条目分成上下两组,顺序恒为:
 * `[浮动条头] 浮动条条目… [更多头] 更多条目…`。条目归属由其相对"更多"分隔行的位置
 * 推导(在分隔行之前=浮动条,之后=更多)。因此:
 * - 在同一区内拖动条目不会带动分隔行(分隔行是独立的行,不再寄生于首个条目);
 * - 某一区为空时其分隔行依然显示,可继续作为拖入目标(不会"消失")。
 * 拖动结束([onClearView])时按当前位置规范化(分隔行归位)并落盘。
 */
class TextSelectMenuConfigDialog : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { MenuAdapter(requireContext()) }
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.text_select_menu_config)
        initView()
        initMenu()
        initData()
    }

    private fun initView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        val itemTouchCallback = ItemTouchCallback(adapter)
        // 仅用拖拽手柄发起拖动:分隔行没有手柄,因此不会被拖动
        itemTouchCallback.isCanDrag = false
        itemTouchHelper = ItemTouchHelper(itemTouchCallback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)
    }

    private fun initMenu() {
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.toolBar.inflateMenu(R.menu.text_select_menu_config)
        binding.toolBar.menu.applyTint(requireContext())
    }

    private fun initData() {
        val config = loadTextSelectMenuConfig(requireContext()).normalized()
        adapter.setItems(rowsOf(toEntries(config.bar), toEntries(config.more)))
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_reset_text_select -> {
                val config = TextSelectMenuConfig.default()
                commitRows(toEntries(config.bar), toEntries(config.more))
            }
        }
        return true
    }

    /** 把内置项 key 列表转成条目行(丢弃未知 key) */
    private fun toEntries(keys: List<String>): MutableList<Row.Entry> =
        keys.mapNotNull { key ->
            TextSelectMenuItem.byKey[key]?.let { Row.Entry(it.key, it.titleRes) }
        }.toMutableList()

    /** 规范顺序:浮动条头 + 浮动条条目 + 更多头 + 更多条目 */
    private fun rowsOf(bar: List<Row.Entry>, more: List<Row.Entry>): List<Row> {
        val rows = ArrayList<Row>(bar.size + more.size + 2)
        rows.add(Row.Divider(ZONE_BAR))
        rows.addAll(bar)
        rows.add(Row.Divider(ZONE_MORE))
        rows.addAll(more)
        return rows
    }

    /** 读取当前列表,按"更多"分隔行把条目拆成 浮动条/更多 两组(各自保持现有顺序) */
    private fun splitEntries(): Pair<MutableList<Row.Entry>, MutableList<Row.Entry>> {
        val items = adapter.getItems()
        val moreIndex = items.indexOfFirst { it is Row.Divider && it.zone == ZONE_MORE }
        if (moreIndex < 0) {
            return items.filterIsInstance<Row.Entry>().toMutableList() to mutableListOf()
        }
        val bar = items.subList(0, moreIndex).filterIsInstance<Row.Entry>().toMutableList()
        val more = items.subList(moreIndex + 1, items.size)
            .filterIsInstance<Row.Entry>().toMutableList()
        return bar to more
    }

    /** 用规范顺序重建列表并落盘 */
    private fun commitRows(bar: List<Row.Entry>, more: List<Row.Entry>) {
        adapter.setItems(rowsOf(bar, more))
        saveTextSelectMenuConfig(
            requireContext(),
            TextSelectMenuConfig(bar.map { it.key }, more.map { it.key })
        )
    }

    /** 拖动结束:按当前位置规范化(分隔行归位)并落盘 */
    private fun normalizeAndPersist() {
        val (bar, more) = splitEntries()
        commitRows(bar, more)
    }

    /** 点箭头:把某个条目移到分界线另一侧 */
    private fun moveAcrossDivider(position: Int) {
        val entry = adapter.getItem(position) as? Row.Entry ?: return
        val (bar, more) = splitEntries()
        if (bar.any { it.key == entry.key }) {
            bar.removeAll { it.key == entry.key }
            more.add(0, entry)
        } else {
            more.removeAll { it.key == entry.key }
            bar.add(entry)
        }
        commitRows(bar, more)
    }

    /** 列表行:常驻分隔行 + 可拖动的条目行,共用同一 item 布局 */
    private sealed interface Row {
        data class Divider(val zone: Int) : Row
        data class Entry(val key: String, val titleRes: Int) : Row
    }

    @SuppressLint("ClickableViewAccessibility")
    private inner class MenuAdapter(context: Context) :
        RecyclerAdapter<Row, ItemTextSelectMenuConfigBinding>(context),
        ItemTouchCallback.Callback {

        override fun getViewBinding(parent: ViewGroup): ItemTextSelectMenuConfigBinding {
            return ItemTextSelectMenuConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextSelectMenuConfigBinding,
            item: Row,
            payloads: MutableList<Any>
        ) {
            when (item) {
                is Row.Divider -> {
                    binding.llEntry.gone()
                    binding.tvSection.visible()
                    binding.tvSection.setText(
                        if (item.zone == ZONE_BAR) R.string.text_menu_zone_bar
                        else R.string.text_menu_zone_more
                    )
                }

                is Row.Entry -> {
                    binding.tvSection.gone()
                    binding.llEntry.visible()
                    binding.tvTitle.text = context.getString(item.titleRes)
                    val items = getItems()
                    val moreIndex = items.indexOfFirst { it is Row.Divider && it.zone == ZONE_MORE }
                    val inBar = moreIndex < 0 || holder.layoutPosition < moreIndex
                    binding.ivMove.setImageResource(
                        if (inBar) R.drawable.ic_arrow_drop_down else R.drawable.ic_arrow_drop_up
                    )
                }
            }
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemTextSelectMenuConfigBinding
        ) {
            binding.ivMove.setOnClickListener {
                val position = holder.layoutPosition
                if (getItem(position) is Row.Entry) {
                    moveAcrossDivider(position)
                }
            }
            binding.ivDrag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper.startDrag(holder)
                }
                false
            }
        }

        override fun swap(srcPosition: Int, targetPosition: Int): Boolean {
            swapItem(srcPosition, targetPosition)
            return true
        }

        override fun onClearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            normalizeAndPersist()
        }
    }

    companion object {
        private const val ZONE_BAR = 0
        private const val ZONE_MORE = 1
    }
}
