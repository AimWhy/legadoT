package io.legado.app.ui.about

import android.content.Context
import android.graphics.Color
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
import io.legado.app.databinding.DialogAppLogBinding
import io.legado.app.databinding.ItemAppLogBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.model.HttpLogger
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.LogUtils
import io.legado.app.utils.gone
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setRoundBackground
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import splitties.views.bottomPadding
import splitties.views.onClick
import splitties.views.onLongClick
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
        refreshItems()
    }

    private fun initToolbar() = binding.toolBar.run {
        setTitle(R.string.log)
        setTitleTextColor(AppColorScheme.current.onSurface)
        inflateMenu(R.menu.app_log)
        overflowIcon?.setTint(AppColorScheme.current.onSurfaceVariant)
        setOnMenuItemClickListener(this@AppLogDialog)
    }

    private fun refreshItems() {
        adapter.setItems(AppLog.logs)
        upEmptyView()
    }

    private fun upEmptyView() {
        if (adapter.isEmpty()) binding.llEmpty.visible() else binding.llEmpty.gone()
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_clear -> {
                AppLog.clear()
                HttpLogger.clear()
                refreshItems()
            }
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
