package io.legado.app.model.readaloud

import io.legado.app.data.appDb
import io.legado.app.data.entities.RoleCast
import io.legado.app.data.entities.TtsVoice
import io.legado.app.model.ReadAloud
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

/** 一个引擎下的一个音色。音色 id 只在引擎内唯一, 跨引擎的身份靠 [key] */
data class VoiceRef(val engineId: Long, val voice: TtsVoice) {

    val key: String get() = key(engineId, voice.id)

    companion object {
        fun key(engineId: Long, voiceId: String): String = "$engineId:$voiceId"
    }
}

/**
 * 角色到音色的分配。自动打底, 已落库的条目一律保留。
 */
object RoleCastManager {

    /**
     * @param usage 音色 [VoiceRef.key] 到本书已占用次数, 用于避免多个角色撞同一个声音
     * @return null 表示无可用音色, 调用方回退旁白
     */
    fun pickVoice(
        profile: RoleProfile,
        candidates: List<VoiceRef>,
        usage: Map<String, Int>
    ): VoiceRef? {
        if (candidates.isEmpty()) return null
        val byGender = narrow(candidates, profile.gender, TtsVoice.GENDER_UNKNOWN) { it.voice.gender }
        val byAge = narrow(byGender, profile.age, TtsVoice.AGE_UNKNOWN) { it.voice.age }
        return byAge.minWithOrNull(
            compareBy({ usage[it.key] ?: 0 }, { it.key })
        )
    }

    /** 条件为空或筛完为空时保持原集合, 保证永不因画像不匹配而配不到音 */
    private fun narrow(
        candidates: List<VoiceRef>,
        want: String?,
        unknown: String,
        of: (VoiceRef) -> String
    ): List<VoiceRef> {
        if (want.isNullOrBlank() || want == unknown) return candidates
        val hit = candidates.filter { of(it) == want }
        return hit.ifEmpty { candidates }
    }

    /** 只有列出音色的引擎进入候选; 单音色引擎的角色走旁白引擎 */
    suspend fun availableVoices(): List<VoiceRef> = withContext(IO) {
        appDb.httpTTSDao.all.flatMap { tts ->
            TtsVoice.parseList(tts.voices).map { VoiceRef(tts.id, it) }
        }
    }

    suspend fun castOf(bookUrl: String): Map<String, RoleCast> = withContext(IO) {
        appDb.roleCastDao.getByBook(bookUrl).associateBy { it.roleName }
    }

    /**
     * 旁白缺省绑当前朗读引擎。ReadAloud.ttsEngine 为空或非数字(系统 TTS)时落到 0,
     * 与 [RoleCast.ttsEngineId] 默认值一致, 取流侧解析不到该 id 会退回当前引擎
     */
    suspend fun narratorCast(bookUrl: String): RoleCast = withContext(IO) {
        appDb.roleCastDao.get(bookUrl, RoleCast.NARRATOR) ?: RoleCast(
            bookUrl = bookUrl,
            roleName = RoleCast.NARRATOR,
            ttsEngineId = ReadAloud.ttsEngine?.toLongOrNull() ?: 0L
        )
    }

    /** 为尚无 casting 的角色自动配音色; 已有记录一律保留, 只把出场章号推到最新 */
    suspend fun ensureCast(bookUrl: String, roles: List<RoleProfile>, chapterIndex: Int) {
        if (roles.isEmpty()) return
        val existing = castOf(bookUrl).toMutableMap()
        val candidates = availableVoices()
        val narratorEngineId = narratorCast(bookUrl).ttsEngineId
        val usage = HashMap<String, Int>()
        existing.values.forEach { cast ->
            cast.voice?.let {
                val key = VoiceRef.key(cast.ttsEngineId, it)
                usage[key] = (usage[key] ?: 0) + 1
            }
        }
        val toWrite = ArrayList<RoleCast>()
        roles.forEach { profile ->
            val current = existing[profile.name]
            if (current != null) {
                if (current.lastSeenChapter < chapterIndex) {
                    current.lastSeenChapter = chapterIndex
                    toWrite.add(current)
                }
                return@forEach
            }
            val picked = pickVoice(profile, candidates, usage)
            val cast = RoleCast(
                bookUrl = bookUrl,
                roleName = profile.name,
                ttsEngineId = picked?.engineId ?: narratorEngineId,
                voice = picked?.voice?.id,
                gender = profile.gender,
                ageGroup = profile.age,
                isManual = false,
                lastSeenChapter = chapterIndex
            )
            // 占用计数即时累加, 同一批里画像相同的角色因此拿到不同音色
            picked?.let { usage[it.key] = (usage[it.key] ?: 0) + 1 }
            existing[profile.name] = cast
            toWrite.add(cast)
        }
        if (toWrite.isNotEmpty()) {
            withContext(IO) { appDb.roleCastDao.insert(*toWrite.toTypedArray()) }
        }
    }
}
