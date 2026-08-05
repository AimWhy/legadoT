# Legado Reading

This context describes the language used for role-aware spoken reading in Legado.

## Language

**角色化朗读**:
An HTTP online reading mode in which different narrative roles can be assigned different voices. The V1 capability does not include Android system TTS.
_Avoid_: AI 多音色朗读

**角色标注**:
The classification of chapter text into contiguous segments attributed to a narrator or named role. AI is one possible annotator for this process.
_Avoid_: AI 朗读

**外部 AI 标注**:
Role annotation performed by a user-configured remote AI service, which requires sending the chapter text to that service. It is an explicitly enabled capability, not an implicit part of ordinary reading.
_Avoid_: 本地标注, AI 朗读

**章节标注结果**:
The complete role annotation for one chapter, including its segments and role profiles. It is accepted or rejected as a whole rather than mixing successful and failed batches.
_Avoid_: 部分标注

**角色画像**:
The descriptive attributes returned with an identified role, such as gender and age group. A cached annotation result retains both its segments and role profiles so it can be replayed independently.
_Avoid_: 角色配音, 音色

**角色身份**:
A book-scoped narrative role identified by its normalized canonical name. Aliases may be associated with that identity, while gender and age describe the role but do not identify it.
_Avoid_: 角色画像, 发言片段

**角色配音**:
The per-book assignment of an HTTP reading engine and voice to a narrative role. Assignments remain stable when later annotation updates change a role's profile; they are removed with the book and are not inherited by a later import.
_Avoid_: 角色标注, 音色识别

**旁白**:
The book-scoped default narrative role used for non-dialogue text, unknown roles, and annotation fallback. It has its own configurable role casting and defaults to the current HTTP reading engine.
_Avoid_: 未知角色, 默认音色

**HTTP 在线朗读引擎**:
A configurable remote speech synthesis engine used to turn text into playable audio. An engine with no explicit voice list still exposes one assignable default voice.
_Avoid_: AI 引擎, 系统 TTS

**音色候选**:
An assignable voice identified by an HTTP reading engine and an optional engine-specific voice identifier. A missing voice identifier denotes that engine's default voice.
_Avoid_: TTS 引擎, 角色画像
