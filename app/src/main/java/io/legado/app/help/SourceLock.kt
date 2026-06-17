package io.legado.app.help

import io.legado.app.exception.NoStackTraceException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock

/**
 * 书源内 single-flight(并发合并):同一 key 并发时只有领跑线程执行 [action],
 * 其余等其完成后直接跳过,自行读取结果。靠代次 epoch 让后来者跳过,无需二次判断。
 *
 * 注意:epoch 只在 [action] 成功后推进,失败不推进、下个等待者接力重试;
 * 锁释放在 finally,异常/取消不泄漏;锁与 epoch 为进程内内存态,重启自动重做;
 * tryLock 带超时,等待方不会无限挂起。
 */
object SourceLock {

    private class Flight {
        val lock = ReentrantLock()

        @Volatile
        var epoch = 0L
    }

    private val flights = ConcurrentHashMap<String, Flight>()

    fun singleFlight(key: String, waitMs: Long, action: () -> Unit) {
        val flight = flights.computeIfAbsent(key) { Flight() }
        val entryEpoch = flight.epoch
        if (!flight.lock.tryLock(waitMs, TimeUnit.MILLISECONDS)) {
            throw NoStackTraceException("singleFlight 等待超时: $key (${waitMs}ms)")
        }
        try {
            if (flight.epoch != entryEpoch) return
            action()
            flight.epoch++
        } finally {
            flight.lock.unlock()
        }
    }

    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    /**
     * 书源内互斥锁(串行化):同一 key 并发时逐个排队执行 [action],每个调用都会执行——
     * 与 [singleFlight] 的"领跑者跑一次、其余跳过"相反。适合 read-modify-write 这类
     * 每次都要记账、谁都不能被跳过的写操作(如账号额度扣减),避免并发丢失更新。
     *
     * tryLock 带超时,等待方不会无限挂起;锁释放在 finally,异常/取消不泄漏;
     * 同 key 可重入,但嵌套不同 key 有死锁风险,由调用方负责。
     */
    fun lock(key: String, waitMs: Long, action: () -> Unit) {
        val lock = locks.computeIfAbsent(key) { ReentrantLock() }
        if (!lock.tryLock(waitMs, TimeUnit.MILLISECONDS)) {
            throw NoStackTraceException("lock 等待超时: $key (${waitMs}ms)")
        }
        try {
            action()
        } finally {
            lock.unlock()
        }
    }

    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * 进程内原子轮询计数器:返回自增前的非负序号(到 Int.MAX 后回绕到 0,始终非负,
     * 可直接 % length 取模)。同 key 跨线程、跨执行共享,用于在多账号间轮流分配等场景,
     * 替代书源自己 new AtomicInteger + System.getProperties()。进程内内存态,重启归零。
     */
    fun tick(key: String): Int {
        return counters.computeIfAbsent(key) { AtomicInteger(0) }.getAndIncrement() and Int.MAX_VALUE
    }
}
