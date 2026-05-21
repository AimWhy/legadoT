package io.legado.app.model

import java.util.concurrent.atomic.AtomicLong

object HttpLogger {

    private const val MAX_SIZE = 50
    private val idGenerator = AtomicLong(0)
    private val records = arrayListOf<HttpRecord>()

    val logs: List<HttpRecord>
        @Synchronized get() = records.toList()

    @Synchronized
    fun add(record: HttpRecord) {
        if (records.size >= MAX_SIZE) {
            records.removeAt(records.lastIndex)
        }
        records.add(0, record)
    }

    fun nextId(): Long = idGenerator.incrementAndGet()

    @Synchronized
    fun clear() {
        records.clear()
    }

    @Synchronized
    fun getById(id: Long): HttpRecord? {
        return records.firstOrNull { it.id == id }
    }
}
