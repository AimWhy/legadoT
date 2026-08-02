package io.legado.app.model.readaloud

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ChapterRoleScript
import io.legado.app.data.entities.RoleCast
import io.legado.app.help.ai.AiClient
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.GSON
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.fromJsonArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 章节角色标注。命中缓存直接返回, 未命中调 LLM 并落缓存。
 */
object RoleAnnotator {

    fun contentMd5(paragraphs: List<String>): String =
        MD5Utils.md5Encode(paragraphs.joinToString("\n"))

    /** 缓存只存片段, 角色名由片段反推; 画像留给 roleCasts 的既有记录 */
    fun rolesFrom(segments: List<Segment>): List<RoleProfile> = segments
        .map { it.role }
        .filter { it.isNotBlank() && it != RoleCast.NARRATOR }
        .distinct()
        .map { RoleProfile(it) }

    /** @return null 表示无法标注, 调用方降级为纯旁白 */
    suspend fun annotate(
        bookUrl: String,
        chapterIndex: Int,
        paragraphs: List<String>
    ): RoleScript? {
        if (paragraphs.isEmpty()) return null
        val md5 = contentMd5(paragraphs)
        readCache(bookUrl, chapterIndex, md5)?.let { return it }
        if (!AppConfig.multiRoleReadAloud || !AiClient.isConfigured()) return null
        val system = AppConfig.aiRolePrompt.ifBlank { RolePrompt.DEFAULT_SYSTEM }
        val known = LinkedHashSet<String>()
        val parts = ArrayList<RoleScript>()
        for (range in RolePrompt.chunks(paragraphs.size)) {
            currentCoroutineContext().ensureActive()
            val userPrompt = RolePrompt.buildUser(paragraphs, range, known)
            val part = try {
                RolePrompt.parse(AiClient.chatJson(system, userPrompt), range)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 取消 OkHttp 调用会以 IOException 冒出, ensureActive 把它还原成取消
                currentCoroutineContext().ensureActive()
                AppLog.put("角色标注失败\n${e.localizedMessage}", e)
                return null
            } ?: return null
            part.roles.forEach { known.add(it.name) }
            parts.add(part)
        }
        val merged = RolePrompt.merge(parts)
        val segments = SpeechScript.sanitize(paragraphs, merged.segments)
        writeCache(bookUrl, chapterIndex, md5, segments)
        return RoleScript(segments, merged.roles.ifEmpty { rolesFrom(segments) })
    }

    /** 预取, 任何失败都吞掉 */
    suspend fun prefetch(bookUrl: String, chapterIndex: Int, paragraphs: List<String>) {
        try {
            annotate(bookUrl, chapterIndex, paragraphs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            AppLog.put("角色标注预取失败\n${e.localizedMessage}", e)
        }
    }

    private fun readCache(bookUrl: String, chapterIndex: Int, md5: String): RoleScript? {
        val cached = appDb.chapterRoleScriptDao.get(bookUrl, chapterIndex) ?: return null
        if (cached.contentMd5 != md5) return null
        val segments = GSON.fromJsonArray<Segment>(cached.segmentsJson).getOrNull()
            ?.filterNotNull()
            ?: return null
        if (segments.isEmpty()) return null
        return RoleScript(segments, rolesFrom(segments))
    }

    private fun writeCache(
        bookUrl: String,
        chapterIndex: Int,
        md5: String,
        segments: List<Segment>
    ) {
        appDb.chapterRoleScriptDao.insert(
            ChapterRoleScript(
                bookUrl = bookUrl,
                chapterIndex = chapterIndex,
                contentMd5 = md5,
                segmentsJson = GSON.toJson(segments)
            )
        )
    }
}
