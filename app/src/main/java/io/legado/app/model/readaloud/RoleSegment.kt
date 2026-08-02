package io.legado.app.model.readaloud

/**
 * 段内片段。s/e 为段内字符下标, 前闭后开。
 */
data class Segment(
    val p: Int = 0,
    val s: Int = 0,
    val e: Int = 0,
    val role: String = ""
)

/** LLM 给出的角色画像, 取值域见 TtsVoice 的 gender/age 常量 */
data class RoleProfile(
    val name: String = "",
    val gender: String? = null,
    val age: String? = null
)

data class RoleScript(
    val segments: List<Segment> = emptyList(),
    val roles: List<RoleProfile> = emptyList()
)
