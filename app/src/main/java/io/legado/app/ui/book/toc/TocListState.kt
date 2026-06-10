package io.legado.app.ui.book.toc

import io.legado.app.data.entities.BookChapter

class TocListState {

    private data class VolumeGroup(
        val volume: BookChapter?,
        val chapters: List<BookChapter>
    )

    private var fullChapters: List<BookChapter> = emptyList()
    private var groups: List<VolumeGroup> = emptyList()
    private var parentVolumeByChapterIndex: Map<Int, Int> = emptyMap()
    private var volumeGroupByIndex: Map<Int, VolumeGroup> = emptyMap()
    private val collapsedVolumeIndexes = mutableSetOf<Int>()
    private var collapseInitialized = false

    var visibleItems: List<TocListItem> = emptyList()
        private set

    fun hasFullChapters(): Boolean = fullChapters.isNotEmpty()

    fun setFullChapters(
        chapters: List<BookChapter>,
        durChapterIndex: Int,
        resetCollapse: Boolean = false
    ) {
        fullChapters = chapters
        groups = buildGroups(chapters)
        rebuildIndexes()
        val currentVolumeIndexes = groups.mapNotNull { it.volume?.index }.toSet()
        if (resetCollapse || !collapseInitialized) {
            resetDefaultCollapse(durChapterIndex)
            collapseInitialized = true
        } else {
            collapsedVolumeIndexes.retainAll(currentVolumeIndexes)
        }
    }

    fun showNormal(durChapterIndex: Int): List<TocListItem> {
        val items = mutableListOf<TocListItem>()
        groups.forEach { group ->
            val volume = group.volume
            if (volume == null) {
                group.chapters.forEach { chapter ->
                    items.add(TocListItem.Chapter(chapter = chapter, depth = 0))
                }
            } else {
                val collapsed = collapsedVolumeIndexes.contains(volume.index)
                items.add(
                    TocListItem.Volume(
                        chapter = volume,
                        depth = 0,
                        collapsed = collapsed,
                        chapterCount = group.chapters.size,
                        containsDurChapter = volume.index == durChapterIndex ||
                                group.chapters.any { it.index == durChapterIndex }
                    )
                )
                if (!collapsed) {
                    group.chapters.forEach { chapter ->
                        items.add(
                            TocListItem.Chapter(
                                chapter = chapter,
                                depth = 1,
                                parentVolumeIndex = volume.index
                            )
                        )
                    }
                }
            }
        }
        visibleItems = items
        return items
    }

    fun showSearch(searchResults: List<BookChapter>, durChapterIndex: Int): List<TocListItem> {
        val childMatchCountByParent = searchResults
            .asSequence()
            .filterNot { it.isVolume }
            .mapNotNull { chapter ->
                parentVolumeByChapterIndex[chapter.index]?.let { parent -> parent to chapter }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.size }
        val matchedVolumeIndexes = searchResults
            .asSequence()
            .filter { it.isVolume }
            .map { it.index }
            .toSet()
        val addedVolumeIndexes = mutableSetOf<Int>()
        val items = mutableListOf<TocListItem>()

        fun addVolumeContext(volumeIndex: Int) {
            if (!addedVolumeIndexes.add(volumeIndex)) return
            val group = volumeGroupByIndex[volumeIndex] ?: return
            val volume = group.volume ?: return
            items.add(
                TocListItem.Volume(
                    chapter = volume,
                    depth = 0,
                    collapsed = false,
                    chapterCount = group.chapters.size,
                    matchedCount = childMatchCountByParent[volumeIndex] ?: 0,
                    matchedSelf = matchedVolumeIndexes.contains(volumeIndex),
                    containsDurChapter = volume.index == durChapterIndex ||
                            group.chapters.any { it.index == durChapterIndex }
                )
            )
        }

        searchResults.forEach { chapter ->
            if (chapter.isVolume) {
                addVolumeContext(chapter.index)
            } else {
                val parentVolumeIndex = parentVolumeByChapterIndex[chapter.index]
                if (parentVolumeIndex != null) {
                    addVolumeContext(parentVolumeIndex)
                }
                items.add(
                    TocListItem.Chapter(
                        chapter = chapter,
                        depth = if (parentVolumeIndex == null) 0 else 1,
                        parentVolumeIndex = parentVolumeIndex
                    )
                )
            }
        }

        visibleItems = items
        return items
    }

    fun toggleVolume(volumeIndex: Int): Boolean {
        if (!volumeGroupByIndex.containsKey(volumeIndex)) return false
        if (collapsedVolumeIndexes.contains(volumeIndex)) {
            collapsedVolumeIndexes.remove(volumeIndex)
        } else {
            collapsedVolumeIndexes.add(volumeIndex)
        }
        return true
    }

    fun expandVolumeContainingChapter(chapterIndex: Int): Boolean {
        val volumeIndex = parentVolumeByChapterIndex[chapterIndex]
            ?: fullChapters.firstOrNull { it.index == chapterIndex && it.isVolume }?.index
            ?: return false
        return collapsedVolumeIndexes.remove(volumeIndex)
    }

    fun isVolumeCollapsed(volumeIndex: Int): Boolean {
        return collapsedVolumeIndexes.contains(volumeIndex)
    }

    fun parentVolumeIndexOf(chapterIndex: Int): Int? {
        return parentVolumeByChapterIndex[chapterIndex]
    }

    fun findVisiblePositionByChapterIndex(chapterIndex: Int): Int {
        return visibleItems.indexOfFirst {
            it is TocListItem.Chapter && it.chapter.index == chapterIndex
        }
    }

    fun findVisiblePositionByVolumeIndex(volumeIndex: Int): Int {
        return visibleItems.indexOfFirst {
            it is TocListItem.Volume && it.chapter.index == volumeIndex
        }
    }

    fun findFallbackVisiblePositionForChapterIndex(chapterIndex: Int): Int {
        val chapterPosition = findVisiblePositionByChapterIndex(chapterIndex)
        if (chapterPosition >= 0) return chapterPosition
        val volumePosition = findVisiblePositionByVolumeIndex(chapterIndex)
        if (volumePosition >= 0) return volumePosition
        val parentVolumeIndex = parentVolumeByChapterIndex[chapterIndex] ?: return -1
        return findVisiblePositionByVolumeIndex(parentVolumeIndex)
    }

    fun findVisiblePositionByItemKey(key: String): Int {
        return visibleItems.indexOfFirst { it.key == key }
    }

    private fun buildGroups(chapters: List<BookChapter>): List<VolumeGroup> {
        val result = mutableListOf<VolumeGroup>()
        var currentVolume: BookChapter? = null
        var currentChapters = mutableListOf<BookChapter>()

        fun flush() {
            if (currentVolume != null || currentChapters.isNotEmpty()) {
                result.add(VolumeGroup(currentVolume, currentChapters.toList()))
            }
        }

        chapters.forEach { chapter ->
            if (chapter.isVolume) {
                flush()
                currentVolume = chapter
                currentChapters = mutableListOf()
            } else {
                currentChapters.add(chapter)
            }
        }
        flush()
        return result
    }

    private fun rebuildIndexes() {
        val parentMap = mutableMapOf<Int, Int>()
        val volumeMap = mutableMapOf<Int, VolumeGroup>()
        groups.forEach { group ->
            val volume = group.volume
            if (volume != null) {
                volumeMap[volume.index] = group
                group.chapters.forEach { chapter ->
                    parentMap[chapter.index] = volume.index
                }
            }
        }
        parentVolumeByChapterIndex = parentMap
        volumeGroupByIndex = volumeMap
    }

    private fun resetDefaultCollapse(durChapterIndex: Int) {
        collapsedVolumeIndexes.clear()
        val currentVolumeIndex = parentVolumeByChapterIndex[durChapterIndex]
            ?: fullChapters.firstOrNull { it.index == durChapterIndex && it.isVolume }?.index
        groups.mapNotNull { it.volume?.index }.forEach { volumeIndex ->
            if (volumeIndex != currentVolumeIndex) {
                collapsedVolumeIndexes.add(volumeIndex)
            }
        }
    }
}
