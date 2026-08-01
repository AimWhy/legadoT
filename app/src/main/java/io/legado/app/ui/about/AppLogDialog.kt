package io.legado.app.ui.about

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogAppLogBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.model.HttpLogger
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.LogUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.observeEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setRoundBackground
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import splitties.views.bottomPadding
import splitties.views.onClick
import splitties.views.onLongClick
import java.io.File
import java.util.Date

/**
 * 应用日志贴底面板。列表新在前;条目点击看全文,长按复制。
 */
class AppLogDialog : BaseDialogFragment(R.layout.dialog_app_log),
    Toolbar.OnMenuItemClickListener {

    /** 贴底面板自设背景与 gravity,豁免统一圆角模板 */
    override val dialogForm = DialogForm.SELF_MANAGED

    private val binding by viewBinding(DialogAppLogBinding::bind)
    private val adapter by lazy { LogAdapter(requireContext()) }
    private var filter = FILTER_ALL

    companion object {
        private const val FILTER_ALL = 0
        private const val FILTER_ERROR = 1
        private const val FILTER_HTTP = 2
        private const val FILTER_SOURCE = 3
        private const val KEY_FILTER = "filter"
        private const val MAX_SHARE_TEXT = 64_000
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.9f).toInt()
            )
            // 面板铺到导航栏/手势条后,避让由视图侧 bottomPadding 负责;
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
        // 浮动 Dialog 窗不派发 insets,监听不会触发,从宿主 Activity decorView 直取
        val navBottom = activity?.window?.decorView?.let {
            ViewCompat.getRootWindowInsets(it)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom
        } ?: 0
        binding.root.bottomPadding += navBottom
        initToolbar()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        filter = savedInstanceState?.getInt(KEY_FILTER) ?: FILTER_ALL
        initFilterChips()
        binding.btnEnableRecord.onClick { setRecordLog(true) }
        // 先注册观察后取快照,配 id 单调守卫,交叠期不重不漏
        observeEvent<AppLog.Entry>(EventBus.APP_LOG_ENTRY) { onNewEntry(it) }
        refreshItems()
    }

    private fun initToolbar() = binding.toolBar.run {
        setTitle(R.string.log)
        setTitleTextColor(AppColorScheme.current.onSurface)
        inflateMenu(R.menu.app_log)
        menu.findItem(R.id.menu_record_log)?.isChecked = AppConfig.recordLog
        overflowIcon?.setTint(AppColorScheme.current.onSurfaceVariant)
        setOnMenuItemClickListener(this@AppLogDialog)
    }

    private val filterChips
        get() = binding.run { listOf(tvFilterAll, tvFilterError, tvFilterHttp, tvFilterSource) }

    private fun initFilterChips() {
        filterChips.forEachIndexed { i, chip ->
            chip.onClick {
                if (filter == i) return@onClick
                filter = i
                renderFilterChips()
                refreshItems()
            }
        }
        renderFilterChips()
    }

    private fun renderFilterChips() {
        filterChips.forEachIndexed { i, chip ->
            chip.background = chipBackground(selected = i == filter)
            chip.setTextColor(contentColor(selected = i == filter))
            chip.typeface = if (i == filter) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
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

    private fun passFilter(entry: AppLog.Entry): Boolean = when (filter) {
        FILTER_ERROR -> entry.isError
        FILTER_HTTP -> entry.isHttp
        FILTER_SOURCE -> entry.isSource
        else -> true
    }

    private fun onNewEntry(entry: AppLog.Entry) {
        // id 单调守卫:快照/事件交叠期的迟到重复事件直接丢弃
        val topId = adapter.getItem(0)?.id ?: 0L
        if (entry.id <= topId) return
        if (!passFilter(entry)) return
        val layoutManager = binding.recyclerView.layoutManager as LinearLayoutManager
        val atTop = layoutManager.findFirstCompletelyVisibleItemPosition() <= 0
        adapter.addItems(0, listOf(entry))
        if (atTop) {
            binding.recyclerView.scrollToPosition(0)
        }
        upEmptyView()
    }

    private fun setRecordLog(value: Boolean) {
        // recordLog 是缓存 var,直赋立即生效;落盘必须另走偏好写入
        AppConfig.recordLog = value
        putPrefBoolean(PreferKey.recordLog, value)
        binding.toolBar.menu.findItem(R.id.menu_record_log)?.isChecked = value
        upEmptyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_FILTER, filter)
    }

    private fun refreshItems() {
        adapter.setItems(AppLog.logs.filter { passFilter(it) })
        upEmptyView()
    }

    private fun upEmptyView() {
        if (!adapter.isEmpty()) {
            binding.llEmpty.gone()
            return
        }
        binding.llEmpty.visible()
        if (filter == FILTER_SOURCE && !AppConfig.recordLog) {
            binding.tvEmptyMsg.setText(R.string.source_log_record_hint)
            binding.btnEnableRecord.visible()
        } else {
            binding.tvEmptyMsg.setText(R.string.no_log)
            binding.btnEnableRecord.gone()
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> alert(R.string.clear, R.string.clear_log_confirm) {
                okButton {
                    AppLog.clear()
                    HttpLogger.clear()
                    refreshItems()
                }
                noButton()
            }

            R.id.menu_export -> exportLogs()

            R.id.menu_record_log -> setRecordLog(!AppConfig.recordLog)
        }
        return true
    }

    private fun showDetail(entry: AppLog.Entry) {
        when {
            entry.httpId != null -> {
                val detail = HttpLogger.getById(entry.httpId)?.fullDetail ?: entry.message
                showDialogFragment(TextDialog("HTTP", detail))
            }

            entry.throwable != null -> showDialogFragment(
                TextDialog(
                    getString(R.string.log),
                    "${entry.message}\n\n${entry.throwable.stackTraceToString()}"
                )
            )

            else -> showDialogFragment(TextDialog(getString(R.string.log), entry.message))
        }
    }

    private fun copyEntry(entry: AppLog.Entry) {
        requireContext().sendToClip(buildString {
            append(entry.message)
            entry.throwable?.let { append("\n\n").append(it.stackTraceToString()) }
        })
    }

    private fun exportLogs() {
        val text = AppLog.exportText(AppLog.logs)
        if (text.isBlank()) {
            toastOnUi(R.string.no_log)
            return
        }
        if (text.length <= MAX_SHARE_TEXT) {
            requireContext().share(text, getString(R.string.log))
        } else {
            // 超长文本走缓存文件分享,规避 Intent 载荷上限
            val file = File(requireContext().cacheDir, "applog_${System.currentTimeMillis()}.txt")
            file.writeText(text)
            requireContext().share(file, "text/plain")
        }
    }

    inner class LogAdapter(context: Context) :
        RecyclerAdapter<AppLog.Entry, ItemAppLogBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemAppLogBinding {
            return ItemAppLogBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemAppLogBinding,
            item: AppLog.Entry,
            payloads: MutableList<Any>
        ) {
            binding.run {
                textTime.text = LogUtils.logTimeFormat.format(Date(item.time))
                if (item.tag == null) {
                    tvTag.gone()
                } else {
                    tvTag.visible()
                    tvTag.text = item.tag
                    tvTag.setTextColor(AppColorScheme.current.primary)
                }
                textMessage.text = item.message
                vBar.setBackgroundColor(barColor(item))
            }
        }

        private fun barColor(item: AppLog.Entry): Int {
            val scheme = AppColorScheme.current
            return when (item.category) {
                AppLog.Entry.Category.ERROR -> scheme.error
                AppLog.Entry.Category.HTTP -> scheme.tertiary
                AppLog.Entry.Category.SOURCE -> scheme.primary
                AppLog.Entry.Category.INFO -> scheme.onSurfaceVariant
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemAppLogBinding) {
            binding.root.onClick {
                getItem(holder.layoutPosition)?.let { showDetail(it) }
            }
            binding.root.onLongClick {
                getItem(holder.layoutPosition)?.let { copyEntry(it) }
            }
        }
    }
}
