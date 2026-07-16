package io.legado.app.utils

/**
 * 尾沿节流:窗口期内多次 submit 只执行最新一次 action,窗口由外部调度器计时。
 * 非线程安全,须在同一线程使用(主线程场景)。
 */
class TrailingThrottle(
    private val intervalMs: Long,
    private val schedule: (Runnable, Long) -> Unit,
    private val unschedule: (Runnable) -> Unit,
) {

    private var pending: (() -> Unit)? = null
    private var scheduledFlag = false
    private val runnable = Runnable {
        scheduledFlag = false
        val action = pending
        pending = null
        action?.invoke()
    }

    fun submit(action: () -> Unit) {
        pending = action
        if (!scheduledFlag) {
            scheduledFlag = true
            schedule(runnable, intervalMs)
        }
    }

    fun cancel() {
        pending = null
        if (scheduledFlag) {
            scheduledFlag = false
            unschedule(runnable)
        }
    }
}
