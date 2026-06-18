package io.legado.app.ui.config

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityBottomBarSkinAssignBinding
import io.legado.app.databinding.DialogBottomBarSkinPaletteBinding
import io.legado.app.databinding.ItemBottomBarSkinAssignRowBinding
import io.legado.app.databinding.ItemBottomBarSkinPaletteBinding
import io.legado.app.help.BottomBarSkinManager
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 分配图集页:导入 zip 后,用户给 4 个底栏按钮各选「选中(必)/未选(可空)」图,保存为一套图集。
 * 图片素材取自 [BottomBarSkinManager] 的暂存目录,缩略图预解码缓存在 [thumbs]。
 */
class BottomBarSkinAssignActivity : BaseActivity<ActivityBottomBarSkinAssignBinding>() {

    override val binding by viewBinding(ActivityBottomBarSkinAssignBinding::inflate)

    private val thumbSizePx by lazy { 56.dpToPx() }

    /** 4 个按钮的槽位与标题(顺序 = 底栏 Tab 顺序) */
    private val slotLabels = linkedMapOf(
        "bookshelf" to R.string.bookshelf,
        "home" to R.string.discovery,
        "notes" to R.string.rss,
        "settings" to R.string.my,
    )

    private val adapter by lazy { RowAdapter(this) }
    private var paletteImages: List<File> = emptyList()
    private var thumbs: Map<File, Bitmap> = emptyMap()
    private var rows: List<RowState> = emptyList()

    /** 编辑模式: 要覆盖写入的图集名; null = 新建导入 */
    private val editName: String? by lazy { intent.getStringExtra("editName") }

    class RowState(
        val slot: String,
        val labelRes: Int,
        var selected: File?,
        var normal: File?,
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.etName.setText(intent.getStringExtra("name").orEmpty())
        if (editName != null) {
            // 编辑: 预填原名, 可改名(改名 = 另存新名 + 删原图集 + 在用则迁移); 标题改为「编辑」
            binding.titleBar.title = getString(R.string.edit)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        loadData()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.bottom_bar_skin_assign, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menu_save) save()
        return super.onCompatOptionsItemSelected(item)
    }

    private fun loadData() {
        lifecycleScope.launch {
            val imgs = withContext(IO) { BottomBarSkinManager.stagingImages() }
            val t = withContext(IO) {
                imgs.mapNotNull { f -> BottomBarSkinManager.previewBitmap(f, thumbSizePx)?.let { f to it } }
                    .toMap()
            }
            val decodable = t.keys.toList()
            val prefill = withContext(IO) { BottomBarSkinManager.buildPrefill(decodable) }
            paletteImages = decodable
            thumbs = t
            rows = slotLabels.map { (slot, labelRes) ->
                RowState(slot, labelRes, prefill[slot]?.selected, prefill[slot]?.normal)
            }
            adapter.setItems(rows)
        }
    }

    private fun save() {
        val assigns = LinkedHashMap<String, BottomBarSkinManager.SlotAssign>()
        rows.forEach { r ->
            r.selected?.let { sel -> assigns[r.slot] = BottomBarSkinManager.SlotAssign(sel, r.normal) }
        }
        if (assigns.isEmpty()) {
            toastOnUi(R.string.bottom_bar_skin_need_selected)
            return
        }
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        lifecycleScope.launch {
            val result = withContext(IO) { BottomBarSkinManager.saveSkin(name, assigns, editName) }
            result.onSuccess { saved ->
                // 新建即启用; 编辑不抢占当前启用项, 仅发事件刷新(若改的正是在用图集)
                if (editName == null) BottomBarSkinManager.active = saved
                postEvent(EventBus.BOTTOM_BAR_SKIN, "")
                finish()
            }.onFailure {
                toastOnUi(R.string.bottom_bar_skin_invalid)
            }
        }
    }

    /** 弹底部调色板; allowClear 时顶部显示「清除」(回调 null) */
    private fun showPalette(allowClear: Boolean, onPick: (File?) -> Unit) {
        val dialog = BottomSheetDialog(this)
        val db = DialogBottomBarSkinPaletteBinding.inflate(layoutInflater)
        db.tvClear.visibility = if (allowClear) View.VISIBLE else View.GONE
        db.tvClear.setOnClickListener {
            onPick(null)
            dialog.dismiss()
        }
        db.recyclerView.layoutManager = GridLayoutManager(this, 4)
        db.recyclerView.adapter = PaletteAdapter(this) { f ->
            onPick(f)
            dialog.dismiss()
        }
        dialog.setContentView(db.root)
        dialog.show()
    }

    private fun bindThumb(iv: ImageView, file: File?) {
        val bmp = file?.let { thumbs[it] }
        if (bmp != null) {
            iv.clearColorFilter()
            iv.setImageBitmap(bmp)
        } else {
            iv.setImageResource(R.drawable.ic_add)
            iv.setColorFilter(0xFF999999.toInt())
        }
    }

    inner class RowAdapter(context: Context) :
        RecyclerAdapter<RowState, ItemBottomBarSkinAssignRowBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemBottomBarSkinAssignRowBinding {
            return ItemBottomBarSkinAssignRowBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBottomBarSkinAssignRowBinding,
            item: RowState,
            payloads: MutableList<Any>,
        ) {
            binding.tvTab.setText(item.labelRes)
            bindThumb(binding.ivSelected, item.selected)
            bindThumb(binding.ivNormal, item.normal)
            val hasSel = item.selected != null
            binding.flNormal.alpha = if (hasSel) 1f else 0.4f
            binding.flNormal.isEnabled = hasSel
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemBottomBarSkinAssignRowBinding,
        ) {
            binding.flSelected.setOnClickListener {
                val row = getItem(holder.layoutPosition) ?: return@setOnClickListener
                showPalette(row.selected != null) { picked ->
                    row.selected = picked
                    if (picked == null) row.normal = null   // 清掉选中 → 连带清未选
                    notifyItemChanged(holder.layoutPosition)
                }
            }
            binding.flNormal.setOnClickListener {
                val row = getItem(holder.layoutPosition) ?: return@setOnClickListener
                if (row.selected == null) return@setOnClickListener   // 选中未设时禁用
                showPalette(row.normal != null) { picked ->
                    row.normal = picked
                    notifyItemChanged(holder.layoutPosition)
                }
            }
        }
    }

    inner class PaletteAdapter(context: Context, private val onClick: (File) -> Unit) :
        RecyclerAdapter<File, ItemBottomBarSkinPaletteBinding>(context) {

        init {
            setItems(paletteImages)
        }

        override fun getViewBinding(parent: ViewGroup): ItemBottomBarSkinPaletteBinding {
            return ItemBottomBarSkinPaletteBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBottomBarSkinPaletteBinding,
            item: File,
            payloads: MutableList<Any>,
        ) {
            binding.ivPalette.setImageBitmap(thumbs[item])
        }

        override fun registerListener(
            holder: ItemViewHolder,
            binding: ItemBottomBarSkinPaletteBinding,
        ) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(onClick)
            }
        }
    }
}
