package io.legado.app.data.entities

import io.legado.app.constant.AppLog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray

/**
 * 在线朗读引擎自带的音色, 序列化在 HttpTTS.voices 的 JSON 数组里
 */
data class TtsVoice(
    val id: String = "",
    val name: String = "",
    val gender: String = GENDER_UNKNOWN,
    val age: String = AGE_UNKNOWN
) {

    companion object {
        const val GENDER_MALE = "male"
        const val GENDER_FEMALE = "female"
        const val GENDER_UNKNOWN = "unknown"
        const val AGE_UNKNOWN = "unknown"

        private val genders = setOf(GENDER_MALE, GENDER_FEMALE, GENDER_UNKNOWN)
        private val ages = setOf("child", "young", "middle", "old", AGE_UNKNOWN)

        /**
         * 解析边界的可空承载体。Gson 反射适配器对 JSON 显式 null 直接写入字段而不走 Kotlin 默认值,
         * 由它承接空值、在 [toVoice] 里合并, TtsVoice 对外四字段保持非空
         */
        private data class VoiceDto(
            val id: String? = null,
            val name: String? = null,
            val gender: String? = null,
            val age: String? = null
        )

        /** 空表表示该引擎只有一个音色; 畸形输入一律归为空表 */
        fun parseList(json: String?): List<TtsVoice> {
            if (json.isNullOrBlank()) return emptyList()
            val parsed = GSON.fromJsonArray<VoiceDto>(json)
                .onFailure { logQuietly("音色列表解析出错", it) }
                .getOrNull() ?: return emptyList()
            val voices = parsed.mapNotNull { it.toVoice() }
            if (voices.isEmpty() && parsed.isNotEmpty()) {
                logQuietly("音色列表 ${parsed.size} 个条目均缺少有效 id, 已全部丢弃")
            }
            return voices
        }

        /** Editor validation is strict; import/playback parsing remains tolerant through [parseList]. */
        fun validateList(json: String?): Result<List<TtsVoice>> = kotlin.runCatching {
            if (json.isNullOrBlank()) return@runCatching emptyList()
            val parsed = GSON.fromJsonArray<VoiceDto>(json).getOrThrow()
            val ids = HashSet<String>()
            parsed.mapIndexed { index, dto ->
                val id = dto.id?.trim().orEmpty()
                require(id.isNotBlank()) { "第 ${index + 1} 个音色缺少 id" }
                require(ids.add(id)) { "音色 id 重复: $id" }
                validateEnum(dto.gender, genders, "gender", index)
                validateEnum(dto.age, ages, "age", index)
                requireNotNull(dto.toVoice())
            }
        }

        private fun validateEnum(raw: String?, allowed: Set<String>, field: String, index: Int) {
            if (raw.isNullOrBlank()) return
            require(raw.trim().lowercase() in allowed) {
                "第 ${index + 1} 个音色的 $field 值无效: $raw"
            }
        }

        /**
         * 空表既表示单音色, 也表示解析失败或条目全被丢弃, 记一笔日志把后两者暴露出来, 两处文案各异以便在日志面板区分。
         * 日志属旁路副作用, 其失败不参与"绝不抛出"的返回契约; 只截 [Exception], [Error] 照常向上传递
         */
        private fun logQuietly(message: String, e: Throwable? = null) {
            try {
                AppLog.put(message, e)
            } catch (_: Exception) {
            }
        }

        /**
         * id 是音色的身份键, 缺失时返回 null 由调用侧丢弃该条目。
         * id/name 存去除首尾空白后的值, 与枚举归一化的 trim 对齐, 空白不进下游以 id 为键的映射
         */
        private fun VoiceDto.toVoice(): TtsVoice? {
            val voiceId = id?.trim().orEmpty()
            if (voiceId.isBlank()) return null
            return TtsVoice(
                id = voiceId,
                name = name?.trim().orEmpty().ifBlank { voiceId },
                gender = normalizeEnum(gender, genders, GENDER_UNKNOWN),
                age = normalizeEnum(age, ages, AGE_UNKNOWN)
            )
        }

        /**
         * 大小写与首尾空白不参与判定(Edge TTS 导出的是 "Female" 这类首字母大写值),
         * 命中 [allowed] 时存归一化后的固定枚举串, 否则存 [fallback]
         */
        private fun normalizeEnum(raw: String?, allowed: Set<String>, fallback: String): String {
            val value = raw?.trim()?.lowercase() ?: return fallback
            return if (value in allowed) value else fallback
        }

        /**
         * 音色与角色画像按 gender/age 直接比较匹配, 两侧都经此归一化,
         * 取值域的唯一定义点在这里
         */
        fun normalizeGender(raw: String?): String = normalizeEnum(raw, genders, GENDER_UNKNOWN)

        fun normalizeAge(raw: String?): String = normalizeEnum(raw, ages, AGE_UNKNOWN)
    }
}
