package io.legado.app.ui.main.bookshelf

import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.activityViewModels
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogBookshelfConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.ui.main.MainViewModel
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.postEvent
import io.legado.app.utils.setRoundBackground
import io.legado.app.utils.viewbindingdelegate.viewBinding
import splitties.views.bottomPadding

/**
 * 书架布局设置底部面板。视图(列表/网格列数)图标五格、排序 chips、分组样式两段、显示开关。
 * 即时生效:开关与排序点选即写并刷新;视图/分组样式引发的重建攒到面板关闭时统一发,
 * 避免 RECREATE 连带销毁面板自身。
 */
class BookshelfConfigDialog : BaseDialogFragment(R.layout.dialog_bookshelf_config) {

    interface CallBack {
        fun upSort()
    }

    /** 贴底面板自设背景,豁免统一圆角模板 */
    override val dialogForm = DialogForm.SELF_MANAGED

    private val binding by viewBinding(DialogBookshelfConfigBinding::bind)
    private val activityViewModel by activityViewModels<MainViewModel>()
    private val callBack get() = parentFragment as? CallBack

    private var pendingRecreate = false
    private var pendingNotifyMain = false

    private val viewCells
        get() = binding.run {
            listOf(cellViewList, cellViewGrid3, cellViewGrid4, cellViewGrid5, cellViewGrid6)
        }
    private val viewIcons
        get() = binding.run {
            listOf(ivViewList, ivViewGrid3, ivViewGrid4, ivViewGrid5, ivViewGrid6)
        }
    private val viewLabels
        get() = binding.run {
            listOf(tvViewList, tvViewGrid3, tvViewGrid4, tvViewGrid5, tvViewGrid6)
        }
    private val sortChips
        get() = binding.run { listOf(tvSort0, tvSort1, tvSort2, tvSort3, tvSort4, tvSort5) }
    private val groupStyleChips
        get() = binding.run { listOf(tvGroupStyle0, tvGroupStyle1) }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            // 贴底面板铺到导航栏/手势条后,避让由视图侧 bottomPadding 负责;
            // 默认窗框 fitInsetsTypes=systemBars() 会把窗口缩到导航栏上方露缝
            WindowCompat.setDecorFitsSystemWindows(this, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                attr.fitInsetsTypes = 0
                attributes = attr
            }
            @Suppress("DEPRECATION")
            navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // 透明导航栏时华为等 ROM 会叠系统对比度灰罩,关闭之
                isNavigationBarContrastEnforced = false
            }
            WindowInsetsControllerCompat(this, decorView).isAppearanceLightNavigationBars =
                ColorUtils.isColorLight(AppColorScheme.current.surfaceContainerLow)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (!AppConfig.isEInkMode) {
            // eink 由基类 gravity 分支给顶部边框
            binding.root.setRoundBackground(
                AppColorScheme.current.surfaceContainerLow,
                topOnly = true,
                radius = resources.getDimension(R.dimen.radius_xl)
            )
            binding.root.clipToOutline = true
        }
        // 窗口铺到手势条/导航栏后,内容底部垫其高度(clipToPadding=false,底缘仍由面板背景填满)。
        // 浮动 Dialog 窗不派发 insets,监听不会触发,从宿主 Activity decorView 直取
        val navBottom = activity?.window?.decorView?.let {
            ViewCompat.getRootWindowInsets(it)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
        } ?: 0
        binding.root.bottomPadding += navBottom
        binding.vDivider.setBackgroundColor(AppColorScheme.current.outlineVariant)
        if (AppConfig.bookshelfLayout !in viewCells.indices) {
            AppConfig.bookshelfLayout = 0
        }
        if (AppConfig.bookshelfSort !in sortChips.indices) {
            AppConfig.bookshelfSort = 0
        }
        if (AppConfig.bookGroupStyle !in groupStyleChips.indices) {
            AppConfig.bookGroupStyle = 0
        }
        initViewCells()
        initSortChips()
        initGroupStyleChips()
        initSwitches()
    }

    private fun initViewCells() {
        viewCells.forEachIndexed { i, cell ->
            cell.setOnClickListener {
                if (AppConfig.bookshelfLayout == i) return@setOnClickListener
                AppConfig.bookshelfLayout = i
                if (i == 0) {
                    activityViewModel.booksGridRecycledViewPool.clear()
                } else {
                    activityViewModel.booksListRecycledViewPool.clear()
                }
                pendingRecreate = true
                renderViewCells()
            }
        }
        renderViewCells()
    }

    private fun renderViewCells() {
        val selected = AppConfig.bookshelfLayout
        viewCells.forEachIndexed { i, cell ->
            cell.background = chipBackground(selected = i == selected)
            val color = contentColor(selected = i == selected)
            viewIcons[i].imageTintList = ColorStateList.valueOf(color)
            viewLabels[i].setTextColor(color)
            viewLabels[i].typeface =
                if (i == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun initSortChips() {
        sortChips.forEachIndexed { i, chip ->
            chip.setOnClickListener {
                if (AppConfig.bookshelfSort == i) return@setOnClickListener
                AppConfig.bookshelfSort = i
                renderSelection(sortChips, i)
                callBack?.upSort()
            }
        }
        renderSelection(sortChips, AppConfig.bookshelfSort)
    }

    private fun initGroupStyleChips() {
        val labels = resources.getStringArray(R.array.group_style)
        groupStyleChips.forEachIndexed { i, chip ->
            chip.text = labels.getOrNull(i)
            chip.setOnClickListener {
                if (AppConfig.bookGroupStyle == i) return@setOnClickListener
                AppConfig.bookGroupStyle = i
                pendingNotifyMain = true
                renderSelection(groupStyleChips, i)
            }
        }
        renderSelection(groupStyleChips, AppConfig.bookGroupStyle)
    }

    private fun initSwitches() = binding.run {
        swShowUnread.isChecked = AppConfig.showUnread
        swShowLastUpdateTime.isChecked = AppConfig.showLastUpdateTime
        swShowReadProgress.isChecked = AppConfig.showBookshelfReadProgress
        swShowWaitUpBooks.isChecked = AppConfig.showWaitUpCount
        swShowBookshelfFastScroller.isChecked = AppConfig.showBookshelfFastScroller
        swShowUnread.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.showUnread = isChecked
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
        swShowLastUpdateTime.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.showLastUpdateTime = isChecked
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
        swShowReadProgress.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.showBookshelfReadProgress = isChecked
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
        swShowWaitUpBooks.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.showWaitUpCount = isChecked
            activityViewModel.postUpBooksLiveData(true)
        }
        swShowBookshelfFastScroller.setOnCheckedChangeListener { _, isChecked ->
            AppConfig.showBookshelfFastScroller = isChecked
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
    }

    private fun renderSelection(chips: List<TextView>, selected: Int) {
        chips.forEachIndexed { i, chip ->
            chip.background = chipBackground(selected = i == selected)
            chip.setTextColor(contentColor(selected = i == selected))
            chip.typeface = if (i == selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }

    private fun contentColor(selected: Boolean): Int {
        val scheme = AppColorScheme.current
        return when {
            selected && AppConfig.isEInkMode -> Color.WHITE
            selected -> scheme.onPrimaryContainer
            else -> scheme.onSurface
        }
    }

    /** chip 背景:默认 outline 描边,选中 primaryContainer 底;eink 选中反色。带按压 ripple */
    private fun chipBackground(selected: Boolean): RippleDrawable {
        val scheme = AppColorScheme.current
        val radius = resources.getDimension(R.dimen.radius_m)
        val content = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            when {
                selected && AppConfig.isEInkMode -> setColor(Color.BLACK)
                selected -> {
                    setColor(scheme.primaryContainer)
                    setStroke(1.dpToPx(), scheme.primary)
                }
                else -> {
                    setColor(Color.TRANSPARENT)
                    setStroke(1.dpToPx(), scheme.outline)
                }
            }
        }
        val mask = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(Color.WHITE)
        }
        return RippleDrawable(ColorStateList.valueOf(scheme.outlineVariant), content, mask)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (pendingRecreate) {
            postEvent(EventBus.RECREATE, "")
        } else if (pendingNotifyMain) {
            postEvent(EventBus.NOTIFY_MAIN, false)
        }
    }
}
