package io.legado.app.model.readaloud

import io.legado.app.data.entities.RoleCast

/**
 * 段落 + 片段 + casting 合成的可播放序列。纯数据, 无 IO。
 */
class SpeechScript(
    private val paragraphs: List<String>,
    segments: List<Segment>,
    private val cast: Map<String, RoleCast>,
    private val fallback: RoleCast
) {

    private val byParagraph: List<List<Segment>> = run {
        val grouped = segments.groupBy { it.p }
        List(paragraphs.size) { p -> grouped[p]?.sortedBy { it.s } ?: emptyList() }
    }

    fun segmentsOf(paragraphIndex: Int): List<Segment> =
        byParagraph.getOrElse(paragraphIndex) { emptyList() }

    /** startOffsetInParagraph 用于「从段中间朗读」, 只对覆盖它的那个片段起截断作用 */
    fun textOf(seg: Segment, startOffsetInParagraph: Int = 0): String {
        val text = paragraphs.getOrNull(seg.p) ?: return ""
        val from = maxOf(seg.s, startOffsetInParagraph).coerceIn(0, text.length)
        val to = seg.e.coerceIn(from, text.length)
        return text.substring(from, to)
    }

    fun castOf(seg: Segment): RoleCast = cast[seg.role] ?: fallback

    /** 覆盖 posInParagraph 的片段下标; 越界钳到末片段, 无片段返回 0 */
    fun segmentIndexAt(paragraphIndex: Int, posInParagraph: Int): Int {
        val segs = segmentsOf(paragraphIndex)
        if (segs.isEmpty()) return 0
        val index = segs.indexOfFirst { posInParagraph < it.e }
        return if (index < 0) segs.lastIndex else index
    }

    companion object {

        /**
         * 校验并修复 LLM 返回的片段。任一段落不满足「有序、首尾相接、完整覆盖、非空、角色名非空」
         * 即整段归旁白。
         *
         * @return 按 (p, s) 有序, 每个段落下标恰好被其片段完整覆盖
         */
        fun sanitize(paragraphs: List<String>, raw: List<Segment>): List<Segment> {
            val grouped = raw.groupBy { it.p }
            val out = ArrayList<Segment>(paragraphs.size)
            for (p in paragraphs.indices) {
                val text = paragraphs[p]
                val whole = Segment(p, 0, text.length, RoleCast.NARRATOR)
                val segs = grouped[p]?.sortedBy { it.s }
                if (text.isEmpty() || segs.isNullOrEmpty()) {
                    out.add(whole)
                    continue
                }
                var cursor = 0
                var valid = true
                for (seg in segs) {
                    if (seg.s != cursor || seg.e <= seg.s ||
                        seg.e > text.length || seg.role.isBlank()
                    ) {
                        valid = false
                        break
                    }
                    cursor = seg.e
                }
                if (valid && cursor == text.length) out.addAll(segs) else out.add(whole)
            }
            return out
        }

        /** 无标注时的退化脚本: 每段一个旁白片段 */
        fun narratorOnly(paragraphs: List<String>, fallback: RoleCast) = SpeechScript(
            paragraphs = paragraphs,
            segments = sanitize(paragraphs, emptyList()),
            cast = emptyMap(),
            fallback = fallback
        )
    }
}
