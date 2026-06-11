package io.legado.app.ui.main.explore

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.help.source.exploreKinds
import io.legado.app.model.webBook.WebBook
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * 单个容器的界面状态。error 与 books 并存时界面优先展示 books(保留旧数据)
 */
data class ExploreContainerState(
    val container: ExploreContainer,
    val books: List<SearchBook> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModel(application: Application) : BaseViewModel(application) {

    val statesData = MutableLiveData<List<ExploreContainerState>>()
    val upBookshelfLiveData = MutableLiveData<Boolean>()
    val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()

    private val states = linkedMapOf<Long, ExploreContainerState>()
    private val stateMutex = Mutex()
    private val loadingIds = ConcurrentHashMap.newKeySet<Long>()

    init {
        execute {
            appDb.bookDao.flowAll().mapLatest { books ->
                val keys = arrayListOf<String>()
                books.filterNot { it.isNotShelf }.forEach {
                    keys.add("${it.name}-${it.author}")
                    keys.add(it.name)
                    keys.add(it.bookUrl)
                }
                keys
            }.catch {
                AppLog.put("发现页获取书架数据失败\n${it.localizedMessage}", it)
            }.collect {
                bookshelf.clear()
                bookshelf.addAll(it)
                upBookshelfLiveData.postValue(true)
            }
        }
        viewModelScope.launch(IO) {
            appDb.exploreContainerDao.flowEnabled().catch {
                AppLog.put("发现页容器数据出错", it)
            }.collect { containers ->
                onContainersChanged(containers)
            }
        }
    }

    private suspend fun onContainersChanged(containers: List<ExploreContainer>) {
        val toLoad = arrayListOf<ExploreContainer>()
        stateMutex.withLock {
            val old = HashMap(states)
            states.clear()
            containers.forEach { c ->
                val oldState = old[c.id]
                when {
                    oldState == null -> {
                        // 新出现的容器:缓存优先,无缓存才走网络
                        val cached = ExploreContainerHelp.getCachedBooks(c.id)
                        if (cached == null) toLoad.add(c)
                        states[c.id] = ExploreContainerState(
                            container = c,
                            books = cached ?: emptyList(),
                            loading = cached == null
                        )
                    }

                    !sameTarget(oldState.container, c) -> {
                        // 编辑改了书源/分类:旧书作废,重新加载
                        toLoad.add(c)
                        states[c.id] = ExploreContainerState(container = c, loading = true)
                    }

                    else -> states[c.id] = oldState.copy(container = c)
                }
            }
            statesData.postValue(states.values.toList())
        }
        toLoad.forEach { loadContainer(it) }
    }

    fun refreshAll() {
        viewModelScope.launch(IO) {
            val containers = stateMutex.withLock { states.values.map { it.container } }
            containers.forEach { loadContainer(it) }
        }
    }

    fun refreshContainer(id: Long) {
        viewModelScope.launch(IO) {
            val container = stateMutex.withLock { states[id]?.container } ?: return@launch
            loadContainer(container)
        }
    }

    /** 书源/分类指向是否一致(样式等展示属性变化不影响已加载书籍) */
    private fun sameTarget(a: ExploreContainer, b: ExploreContainer): Boolean {
        return a.sourceUrl == b.sourceUrl && a.kindUrl == b.kindUrl && a.kindTitle == b.kindTitle
    }

    private fun loadContainer(container: ExploreContainer) {
        if (!loadingIds.add(container.id)) return
        viewModelScope.launch(IO) {
            upStateIfSameTarget(container) { it.copy(loading = true, error = null) }
            kotlin.runCatching {
                val source = appDb.bookSourceDao.getBookSource(container.sourceUrl)
                    ?: throw NoStackTraceException(
                        context.getString(R.string.explore_source_not_found)
                    )
                val kinds = source.exploreKinds()
                val url = ExploreContainerHelp.resolveKindUrl(
                    kinds, container.kindTitle, container.kindUrl
                )
                withTimeout(30_000L) {
                    WebBook.exploreBookAwait(source, url, 1)
                }
            }.onSuccess { books ->
                val accepted = upStateIfSameTarget(container) {
                    it.copy(books = books, loading = false, error = null)
                }
                if (accepted) {
                    ExploreContainerHelp.putCachedBooks(container.id, books)
                }
            }.onFailure { e ->
                AppLog.put("发现容器[${container.getDisplayTitle()}]加载失败", e)
                upStateIfSameTarget(container) {
                    it.copy(loading = false, error = e.localizedMessage ?: "加载失败")
                }
            }
            loadingIds.remove(container.id)
            // 加载期间容器被编辑改了指向:本次结果已丢弃,换当前指向重新加载
            val current = stateMutex.withLock { states[container.id]?.container }
            if (current != null && !sameTarget(current, container)) {
                loadContainer(current)
            }
        }
    }

    /**
     * 仅当容器当前指向与发起加载时一致才更新状态(也排除已删除的容器),
     * 避免编辑竞态下旧指向的加载结果覆盖新指向
     */
    private suspend fun upStateIfSameTarget(
        loaded: ExploreContainer,
        transform: (ExploreContainerState) -> ExploreContainerState
    ): Boolean {
        stateMutex.withLock {
            val state = states[loaded.id] ?: return false
            if (!sameTarget(state.container, loaded)) return false
            states[loaded.id] = transform(state)
            statesData.postValue(states.values.toList())
        }
        return true
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val key = if (book.author.isNotBlank()) "${book.name}-${book.author}" else book.name
        return bookshelf.contains(key) || bookshelf.contains(book.bookUrl)
    }

    fun deleteContainer(container: ExploreContainer) {
        execute {
            appDb.exploreContainerDao.delete(container)
            ExploreContainerHelp.removeCache(container.id)
        }
    }
}
