package io.legado.app.constant

import android.util.Log
import io.legado.app.BuildConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.LogUtils
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

object AppLog {

    /** 日志条目。谓词可多中(过滤用),category 单优先级(染色用):错误>HTTP>源>信息 */
    data class Entry(
        val id: Long,
        val time: Long,
        val message: String,
        val throwable: Throwable? = null,
        val tag: String? = null,
        val httpId: Long? = null,
        val error: Boolean = false,
    ) {
        enum class Category { ERROR, HTTP, SOURCE, INFO }

        val isError get() = throwable != null || error
        val isHttp get() = httpId != null
        val isSource get() = tag != null

        val category: Category
            get() = when {
                isError -> Category.ERROR
                isHttp -> Category.HTTP
                isSource -> Category.SOURCE
                else -> Category.INFO
            }
    }

    const val MAX_SIZE = 300
    private val idGenerator = AtomicLong(0)
    private val mLogs = arrayListOf<Entry>()

    val logs: List<Entry>
        @Synchronized get() = mLogs.toList()

    @Synchronized
    fun put(
        message: String?,
        throwable: Throwable? = null,
        toast: Boolean = false,
        tag: String? = null,
        httpId: Long? = null,
        error: Boolean = false,
    ) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        val fileMsg = tag?.let { "[$it] $message" } ?: message
        if (throwable == null) {
            LogUtils.d("AppLog", fileMsg)
        } else {
            LogUtils.d("AppLog", "$fileMsg\n${throwable.stackTraceToString()}")
        }
        append(message, throwable, tag, httpId, error)
    }

    @Synchronized
    fun putNotSave(
        message: String?,
        throwable: Throwable? = null,
        toast: Boolean = false,
        tag: String? = null,
        httpId: Long? = null,
        error: Boolean = false,
    ) {
        message ?: return
        if (toast) {
            appCtx.toastOnUi(message)
        }
        append(message, throwable, tag, httpId, error)
    }

    fun putDebug(message: String?, throwable: Throwable? = null, tag: String? = null) {
        if (AppConfig.recordLog) {
            put(message, throwable, tag = tag)
        }
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }

    /** 导出全量日志为可分享文本:时间升序,`[tag或类别] message`,stacktrace 缩进 4 空格 */
    fun exportText(entries: List<Entry>): String {
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        return buildString {
            entries.asReversed().forEach { entry ->
                append(df.format(Date(entry.time)))
                append(" [").append(entry.tag ?: categoryLabel(entry)).append("] ")
                appendLine(entry.message)
                entry.throwable?.let {
                    appendLine(it.stackTraceToString().trimEnd().prependIndent("    "))
                }
            }
        }
    }

    private fun categoryLabel(entry: Entry): String = when (entry.category) {
        Entry.Category.ERROR -> "错误"
        Entry.Category.HTTP -> "HTTP"
        Entry.Category.SOURCE -> "源"
        Entry.Category.INFO -> "信息"
    }

    private fun append(
        message: String,
        throwable: Throwable?,
        tag: String?,
        httpId: Long?,
        error: Boolean,
    ) {
        if (mLogs.size >= MAX_SIZE) {
            mLogs.removeLastOrNull()
        }
        val entry = Entry(
            id = idGenerator.incrementAndGet(),
            time = System.currentTimeMillis(),
            message = message,
            throwable = throwable,
            tag = tag,
            httpId = httpId,
            error = error,
        )
        mLogs.add(0, entry)
        if (BuildConfig.DEBUG) {
            val stackTrace = Thread.currentThread().stackTrace
            Log.e(stackTrace.getOrNull(4)?.className ?: "AppLog", message, throwable)
        }
        // 同步块内发送;后台线程 post 走主线程队列,主线程 post 同步分发,
        // 极端交错下非严格 id 序,消费侧以 id 单调守卫兜底
        postEvent(EventBus.APP_LOG_ENTRY, entry)
    }
}
