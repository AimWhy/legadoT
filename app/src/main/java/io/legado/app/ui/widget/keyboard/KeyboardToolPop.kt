package io.legado.app.ui.widget.keyboard

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.Window
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.PopupKeyboardToolBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.activity
import io.legado.app.utils.applyMd3PopupStyle
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.windowSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import splitties.systemservices.layoutInflater
import splitties.systemservices.windowManager

/**
 * 键盘帮助浮窗
 */
class KeyboardToolPop(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rootView: View,
    private val callBack: CallBack
) : PopupWindow(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
    ViewTreeObserver.OnGlobalLayoutListener {

    private val helpChar = "❓"

    private val binding = PopupKeyboardToolBinding.inflate(LayoutInflater.from(context))
    private val adapter = Adapter(context)
    private var mIsSoftKeyBoardShowing = false
    var initialPadding = 0

    /** 0 表示未装配,首次 upRowCount 必然执行装配 */
    private var rowCount = 0

    /** 用真实 item 测量行高;采样含 CJK,单行高取两类字形度量的较大者 */
    private val measureItem: TextView by lazy {
        ItemFilletTextBinding.inflate(context.layoutInflater).root.apply {
            text = "${helpChar}中"
        }
    }

    init {
        contentView = binding.root
        applyMd3PopupStyle()

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false
        inputMethodMode = INPUT_METHOD_NEEDED //解决遮盖输入法
        initRecyclerView()
        upRowCount()
        upAdapterData()
    }

    fun attachToWindow(window: Window) {
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(this)
        measureContentView()
        observeRowCount()
    }

    override fun onGlobalLayout() {
        val keyboardHeight = resolveKeyboardHeight()
        val preShowing = mIsSoftKeyBoardShowing
        if (keyboardHeight > 0) {
            mIsSoftKeyBoardShowing = true // 超过屏幕五分之一则表示弹出了输入法
            rootView.setPadding(0, 0, 0, initialPadding + contentView.measuredHeight)
            if (!isShowing) {
                showAtLocation(rootView, Gravity.BOTTOM, 0, 0)
            }
        } else {
            mIsSoftKeyBoardShowing = false
            rootView.setPadding(0, 0, 0, 0)
            if (preShowing) {
                dismiss()
            }
        }
    }

    private fun resolveKeyboardHeight(): Int {
        ViewCompat.getRootWindowInsets(rootView)?.let { windowInsets ->
            if (windowInsets.isVisible(WindowInsetsCompat.Type.ime())) {
                return windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            }
            return 0
        }
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)
        val screenHeight = windowManager.windowSize.heightPixels
        val keyboardHeight = screenHeight - rect.bottom
        return if (keyboardHeight > screenHeight / 5) keyboardHeight else 0
    }

    private fun initRecyclerView() {
        binding.recyclerView.adapter = adapter
        adapter.addHeaderView {
            ItemFilletTextBinding.inflate(context.layoutInflater, it, false).apply {
                textView.text = helpChar
                root.setOnClickListener {
                    helpAlert()
                }
            }
        }
    }

    /**
     * 装配显示行数。行数 1 走横向滚动;≥2 走 flexbox 换行并把高度锁成行数 × 行高,
     * 超出行数的按键竖向滚动。
     */
    fun upRowCount(rows: Int = AppConfig.keyboardToolRows) {
        if (rowCount == rows) {
            return
        }
        rowCount = rows
        if (rows <= AppConfig.KEYBOARD_TOOL_MIN_ROWS) {
            binding.recyclerView.layoutManager =
                LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            binding.recyclerView.layoutManager = FlexboxLayoutManager(context).apply {
                flexDirection = FlexDirection.ROW
                flexWrap = FlexWrap.WRAP
            }
            height = measureRowsHeight(rows)
        }
        measureContentView()
        if (isShowing) {
            update(width, contentView.measuredHeight)
            rootView.setPadding(0, 0, 0, initialPadding + contentView.measuredHeight)
        }
    }

    /** 宿主 Activity 同时充当订阅的 LifecycleOwner */
    private fun observeRowCount() {
        contentView.activity?.observeEvent<Int>(PreferKey.keyboardToolRows) { rows ->
            upRowCount(rows)
        }
    }

    /** item 上下 margin 各 3dp(item_fillet_text),RecyclerView 上下 padding 各 space_s */
    private fun measureRowsHeight(rows: Int): Int {
        val unspec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        measureItem.measure(unspec, unspec)
        val rowHeight = measureItem.measuredHeight + 3.dpToPx() * 2
        return rows * rowHeight + binding.recyclerView.paddingTop +
                binding.recyclerView.paddingBottom
    }

    private fun measureContentView() {
        val heightSpec = if (height > 0) {
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        } else {
            View.MeasureSpec.UNSPECIFIED
        }
        contentView.measure(View.MeasureSpec.UNSPECIFIED, heightSpec)
    }

    @Suppress("MemberVisibilityCanBePrivate")
    fun upAdapterData() {
        scope.launch {
            appDb.keyboardAssistsDao.flowByType(0).catch {
                AppLog.put("键盘帮助浮窗获取数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                adapter.setItems(it)
            }
        }
    }

    private fun helpAlert() {
        val items = arrayListOf(
            SelectItem(context.getString(R.string.assists_key_config), "keyConfig")
        )
        items.addAll(callBack.helpActions())
        context.selector(context.getString(R.string.help), items) { _, selectItem, _ ->
            when (selectItem.value) {
                "keyConfig" -> config()
                else -> callBack.onHelpActionSelect(selectItem.value)
            }
        }
    }

    private fun config() {
        contentView.activity?.showDialogFragment<KeyboardAssistsConfig>()
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<KeyboardAssist, ItemFilletTextBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemFilletTextBinding {
            return ItemFilletTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemFilletTextBinding,
            item: KeyboardAssist,
            payloads: MutableList<Any>
        ) {
            binding.run {
                textView.text = item.key
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemFilletTextBinding) {
            holder.itemView.apply {
                setOnClickListener {
                    getItemByLayoutPosition(holder.layoutPosition)?.let {
                        callBack.sendText(it.value)
                    }
                }
            }
        }
    }

    interface CallBack {

        fun helpActions(): List<SelectItem<String>> = arrayListOf()

        fun onHelpActionSelect(action: String)

        fun sendText(text: String)

    }

}
