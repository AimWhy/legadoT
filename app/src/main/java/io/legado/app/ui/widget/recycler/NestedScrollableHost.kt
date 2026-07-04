package io.legado.app.ui.widget.recycler

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * 方向仲裁宿主:起点落在本宿主(及其子控件)上的触摸,在方向未定的歧义窗口内先刚性锁住
 * 父链(防主 tab pager 在子列表进 DRAGGING 前抢走横滑),首次越过 touchSlop 时定向、一次定终身:
 * - 横向为主 → 维持独占到手势结束(封面横滑,绝不切标签;平手偏袒横向);
 * - 纵向为主 → 逐级交还父链,外层竖向列表([RecyclerViewAtPager2])在 DOWN 时已记录起点、
 *   此刻累计位移已超 slop,下一个 MOVE 即拦截接管(框架标准的中途所有权移交),其自身的
 *   "纵向锁 pager"分支继续管住主 pager;下拉刷新也随之恢复。
 *   定向的这口 MOVE 不再下发子列表(吞掉):快速斜滑时 dx 可能同时越过子列表 slop,
 *   子列表会进 DRAGGING 并把父链重新锁死,外层就永远接不了管。
 *
 * 机制:DOWN 与(未交还前的)每个 MOVE 都在 [dispatchTouchEvent] 里把父链逐级置
 * requestDisallowInterceptTouchEvent。两个实现要点,都是绕开框架的"状态一致"假设:
 * 1. 挂 dispatch 而非 onInterceptTouchEvent——子列表起滑进 DRAGGING 时 RecyclerView 会对
 *    父链置 FLAG_DISALLOW_INTERCEPT,首当其冲置位的就是本宿主,此后 ViewGroup 的标志检查
 *    让拦截钩子整段手势不再被调用;dispatch 则对触摸目标链上的每个事件无条件送达。
 * 2. 逐级直调每个祖先而非只调直接父级一次——ViewGroup.requestDisallowInterceptTouchEvent
 *    发现自身标志已相同时直接 return、不再上传("assume our ancestors are too");而外层
 *    [RecyclerViewAtPager2] 横滑>50px 时只清它上方(SwipeRefreshLayout→主 pager)的标志,
 *    它下方仍为 true,单次调用会在已 true 的直接父级止步,被清的上层永远修不回来。
 *    交还(置 false)同理逐级直调,否则同样在标志相同处短路。
 *
 * 与 Google 官方 NestedScrollableHost 的差异:横向不做 canScrollHorizontally 放行——
 * 封面不足一屏时横滑也不切标签(封面区手势语义一致,2026-07-04 验收结论);纵向交还
 * 则与官方同思路,恢复"上下扫容器列表、左右扫封面"的分流。
 */
class NestedScrollableHost @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var initialX = 0f
    private var initialY = 0f

    /** 本段手势方向已定(首次越过 touchSlop 时置位,UP 前不再改判) */
    private var resolved = false

    /** 手势归子控件:未定向阶段与横向定向为 true;纵向定向后为 false,不再重申独占 */
    private var ownsGesture = true

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = e.x
                initialY = e.y
                resolved = false
                ownsGesture = true
                setDisallowInterceptOnAncestors(true)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!resolved) {
                    val dx = abs(e.x - initialX)
                    val dy = abs(e.y - initialY)
                    if (dx > touchSlop || dy > touchSlop) {
                        resolved = true
                        ownsGesture = dx >= dy
                        if (!ownsGesture) {
                            setDisallowInterceptOnAncestors(false)
                            // 吞掉定向 MOVE:不给子列表借斜滑分量进 DRAGGING 重锁父链的机会
                            return true
                        }
                    }
                }
                if (ownsGesture) setDisallowInterceptOnAncestors(true)
            }
        }
        return super.dispatchTouchEvent(e)
    }

    /**
     * 对每个祖先直调 requestDisallowInterceptTouchEvent,而非依赖单次调用沿链上传:
     * ViewGroup 实现里标志已相同即短路返回,遇到"下层 true、上层已被别人清成 false"的
     * 断层时,单次调用修不到上层。
     */
    private fun setDisallowInterceptOnAncestors(disallow: Boolean) {
        var p = parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow)
            p = p.parent
        }
    }

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        // 单子约束:宿主只包裹一个滚动控件
        check(childCount <= 1) { "NestedScrollableHost 只能包裹一个子控件" }
    }
}
