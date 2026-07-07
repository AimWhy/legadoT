package io.legado.app.base.adapter

import androidx.recyclerview.widget.ListUpdateCallback
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N3a Critical 修复回归锚。
 *
 * 修复前 DiffRecyclerAdapter 用 `AsyncListDiffer(this, diffItemCallback)` 构造 differ,
 * androidx 内部会用 `AdapterListUpdateCallback` 包装 `this`,其 onInserted/onRemoved/
 * onMoved/onChanged 把 diff 结果的 position 原样转发给
 * notifyItemRangeXxx / notifyItemMoved —— 不做任何 header 偏移(已对照 recyclerview 1.2.1
 * 源码确认)。本类支持 header 后(详情页内嵌目录 2 个 header),这会导致每次 submitList
 * 都用 list-space 下标去通知一个需要 adapter-space 下标的 RecyclerView,是
 * "Inconsistency detected" 崩溃的已知诱因。
 *
 * 修复把偏移逻辑抽成 [DiffRecyclerAdapter.offsetListUpdateCallback] 纯函数——不触碰
 * LayoutInflater/Looper 等 Android 运行时状态,可以在无 Robolectric 的纯 JVM 单测里
 * 直接调用生产代码实际使用的这份逻辑,而不是测试自建的另一份拷贝。
 */
class DiffRecyclerAdapterHeaderOffsetTest {

    private data class Call(
        val type: String,
        val a: Int,
        val b: Int,
        val payload: Any? = null
    )

    private fun buildCallback(headerCount: Int): Pair<ListUpdateCallback, MutableList<Call>> {
        val calls = mutableListOf<Call>()
        val callback = DiffRecyclerAdapter.offsetListUpdateCallback(
            headerCount = { headerCount },
            onInserted = { position, count -> calls += Call("inserted", position, count) },
            onRemoved = { position, count -> calls += Call("removed", position, count) },
            onMoved = { from, to -> calls += Call("moved", from, to) },
            onChanged = { position, count, payload -> calls += Call("changed", position, count, payload) }
        )
        return callback to calls
    }

    @Test
    fun `insert forwards adapter-space position offset by header count`() {
        val (callback, calls) = buildCallback(headerCount = 2)
        callback.onInserted(0, 3)
        assertEquals(listOf(Call("inserted", 2, 3)), calls)
    }

    @Test
    fun `remove forwards adapter-space position offset by header count`() {
        val (callback, calls) = buildCallback(headerCount = 2)
        callback.onRemoved(5, 1)
        assertEquals(listOf(Call("removed", 7, 1)), calls)
    }

    @Test
    fun `moved forwards both adapter-space positions offset by header count`() {
        val (callback, calls) = buildCallback(headerCount = 2)
        callback.onMoved(0, 4)
        assertEquals(listOf(Call("moved", 2, 6)), calls)
    }

    @Test
    fun `changed forwards adapter-space position and preserves payload`() {
        val (callback, calls) = buildCallback(headerCount = 2)
        val payload = "displayTitle"
        callback.onChanged(3, 2, payload)
        assertEquals(listOf(Call("changed", 5, 2, payload)), calls)
    }

    @Test
    fun `zero headers is a no-op offset for the pre-existing header-less consumers`() {
        // SearchAdapter/ChangeSourceAdapter/CoverAdapter/CacheAdapter/BaseBooksAdapter
        // 均无 header,headerCount 恒为 0,偏移必须退化为直传,行为不变
        val (callback, calls) = buildCallback(headerCount = 0)
        callback.onInserted(0, 1)
        callback.onRemoved(2, 1)
        callback.onMoved(1, 3)
        callback.onChanged(4, 1, null)
        assertEquals(
            listOf(
                Call("inserted", 0, 1),
                Call("removed", 2, 1),
                Call("moved", 1, 3),
                Call("changed", 4, 1, null)
            ),
            calls
        )
    }

    @Test
    fun `reversed list resubmission maps every list-space move through the same header offset`() {
        // toggleTocOrder 整份倒序重提交列表,DiffUtil 通常拆成多个 move;逐条独立换算,
        // 不应随调用次数累积偏移或漂移
        val (callback, calls) = buildCallback(headerCount = 2)
        val moves = listOf(0 to 4, 1 to 3)
        moves.forEach { (from, to) -> callback.onMoved(from, to) }
        assertEquals(
            listOf(Call("moved", 2, 6), Call("moved", 3, 5)),
            calls
        )
    }
}
