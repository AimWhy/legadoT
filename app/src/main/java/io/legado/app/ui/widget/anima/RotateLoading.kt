package io.legado.app.ui.widget.anima

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.google.android.material.loadingindicator.LoadingIndicator
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor

/**
 * RotateLoading
 *
 * 外壳保名：类名/XML 标签/公开 API 与旧手绘转圈完全兼容，内核换成 M3 [LoadingIndicator]。
 * [LoadingIndicator] 是 final 类，故用 FrameLayout 包裹一层而非直接继承。
 *
 * 兼容面实况（见任务 Step 1 discovery，非本文件全部凭空设计；brief 给的 grep 样例本身有盲区，
 * 按 attrs.xml 实际 id（rotate_loading/rl_loading/loading_toc）逐个反查后，实际消费点有 21 个文件）：
 * - 外部消费文件实际调用的是 [visible]/[gone]/[inVisible]（从未直接调用 start/stop/isStarted/hideMode）。
 * - [visible]/[gone] 与 `io.legado.app.utils` 里的同名 View 扩展函数撞名——成员函数优先于扩展函数解析，
 *   因此这两个名字必须由本类显式实现，否则会静默退化成"仅改可见性、不启停动画"的错误行为（编译器对此不报错，
 *   因为签名对得上，这是本类的一个陷阱点）。[inVisible]（大写 V）没有同名扩展，少了它会直接编译失败。
 * - 项目扩展 View.visible(Boolean)/View.gone(Boolean)（1 参版本）不会被本类的零参成员遮蔽——对 RotateLoading
 *   引用调用 1 参版本会绕过 start/stop 语义，勿用。
 * - [start]/[stop]/[isStarted]/[hideMode] 未被外部直接引用，但作为已声明兼容面保留，供上面三个方法复用；
 *   彼此之间只单向调用（visible/gone/inVisible -> start/stop），避免与同名扩展/自身产生递归。
 *
 * eink：[LoadingIndicator.getDrawable] 返回的 `LoadingIndicatorDrawable` 并未实现 `Animatable`，
 * `as? Animatable` 恒为 null。改用它的 `setVisible(visible, restart, animate)` 三参重载，
 * 传 `animate=false` 会立即 `ObjectAnimator.cancel()` + `SpringAnimation.skipToEnd()`，
 * 定格为一个确定的静态形状且不再触发重绘，满足"显示后停止变形、不再闪烁"的要求。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class RotateLoading @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val indicator = LoadingIndicator(context)

    var hideMode = GONE

    var isStarted = false
        private set

    var loadingColor: Int = if (isInEditMode) 0xFF3D7EFF.toInt() else context.accentColor
        set(value) {
            field = value
            indicator.setIndicatorColor(value)
        }

    init {
        // 旧 styleable 继续解析（14 个布局零改动）；仅 loading_color 有意义，
        // loading_width/shadow_position/loading_speed 是手绘实现专用尺寸参数，读取后无对应落点，忽略即可。
        val ta = context.obtainStyledAttributes(attrs, R.styleable.RotateLoading)
        loadingColor = ta.getColor(R.styleable.RotateLoading_loading_color, loadingColor)
        ta.recycle()
        addView(
            indicator,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun start() {
        isStarted = true
        visibility = VISIBLE
        if (AppConfig.isEInkMode) {
            post { indicator.drawable.setVisible(true, false, false) }
        }
    }

    fun stop() {
        isStarted = false
        visibility = if (hideMode == INVISIBLE) INVISIBLE else GONE
    }

    fun visible() {
        start()
    }

    fun gone() {
        hideMode = GONE
        stop()
    }

    fun inVisible() {
        hideMode = INVISIBLE
        stop()
    }
}
