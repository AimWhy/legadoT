package io.legado.app.data.entities

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

        /** 空表表示该引擎只有一个音色; 畸形输入一律归为空表 */
        fun parseList(json: String?): List<TtsVoice> {
            if (json.isNullOrBlank()) return emptyList()
            val parsed = GSON.fromJsonArray<TtsVoice>(json).getOrNull() ?: return emptyList()
            return parsed.filterNotNull()
                .filter { it.id.isNotBlank() }
                .map { normalize(it) }
        }

        private fun normalize(v: TtsVoice) = v.copy(
            name = v.name.ifBlank { v.id },
            gender = if (v.gender in genders) v.gender else GENDER_UNKNOWN,
            age = if (v.age in ages) v.age else AGE_UNKNOWN
        )
    }
}
