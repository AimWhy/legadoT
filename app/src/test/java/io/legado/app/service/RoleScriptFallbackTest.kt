package io.legado.app.service

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoleScriptFallbackTest {

    private fun readSource(name: String): String {
        val candidates = listOf(
            File("src/main/java/io/legado/app/service/$name"),
            File("app/src/main/java/io/legado/app/service/$name")
        )
        return candidates.first { it.isFile }.readText()
    }

    private val base by lazy { readSource("BaseReadAloudService.kt") }
    private val http by lazy { readSource("HttpReadAloudService.kt") }

    /** 脚本就绪与四道降级同属 prepareSpeechScript 一个入口, 实现落在 buildScriptFor */
    private val buildScript by lazy {
        base.substringAfter("internal suspend fun buildScriptFor(").substringBefore("\n    }")
    }
    private val prepareScript by lazy {
        base.substringAfter("internal suspend fun prepareSpeechScript()").substringBefore("\n    }")
    }

    @Test
    fun `the switch being off short circuits before any network call`() {
        val gateAt = buildScript.indexOf("!AppConfig.multiRoleReadAloud")
        val annotateAt = buildScript.indexOf("RoleAnnotator.annotate")
        assertTrue("未在开关处直接退化", gateAt >= 0)
        assertTrue("开关判定未先于标注调用", annotateAt > gateAt)
    }

    @Test
    fun `every failure path degrades to a narrator only script`() {
        // 无书 / 开关关 / 章号未知 / 标注返回 null 四条路径都要落到 narratorOnly
        assertTrue(
            "缺纯旁白退化脚本",
            buildScript.contains("SpeechScript.narratorOnly(paragraphs, fallback)")
        )
        assertTrue(
            "无书/开关关/章号未知三路未合并降级",
            buildScript.contains("book == null || !AppConfig.multiRoleReadAloud || chapterIndex < 0")
        )
        assertTrue("标注失败未降级", buildScript.contains("?: return narratorOnly"))
        // casting 取库异常与标注抛错同样不得中断朗读, 取消照旧向上传播
        assertTrue("配音取库异常未降级", buildScript.contains("catch (e: Exception)"))
        assertTrue("取消未向上传播", buildScript.contains("catch (e: CancellationException)"))
    }

    @Test
    fun `casting is ensured before the script is built`() {
        val ensureAt = buildScript.indexOf("RoleCastManager.ensureCast")
        val castOfAt = buildScript.indexOf("RoleCastManager.castOf")
        assertTrue("未落 casting", ensureAt >= 0)
        assertTrue("脚本未用真 casting", castOfAt > ensureAt)
    }

    @Test
    fun `annotation stays off the playback callback path`() {
        // currentScript 跑在 ExoPlayer 回调线程上, 标注与取库只挂在挂起入口上
        assertTrue("currentScript 不得为 suspend", !base.contains("suspend fun currentScript()"))
        val currentScript = base.substringAfter("fun currentScript(): SpeechScript")
            .substringBefore("\n    }")
        assertTrue("currentScript 内不得标注", !currentScript.contains("RoleAnnotator"))
        assertTrue("currentScript 内不得取库", !currentScript.contains("RoleCastManager"))
        assertTrue("脚本就绪入口未接标注", prepareScript.contains("buildScriptFor("))
    }

    @Test
    fun `the next chapter is annotated before its audio is pre downloaded`() {
        val preDownload = http.substringAfter("private suspend fun preDownloadAudios()")
            .substringBefore("\n    }")
        val buildAt = preDownload.indexOf("buildScriptFor(nextChapter.chapter.index")
        val streamAt = preDownload.indexOf("getSpeakStream(")
        assertTrue("缺下一章预取", buildAt >= 0)
        assertTrue("音频先于标注下载, 起播时缓存会全落空", streamAt > buildAt)
        // 段落表须与起播时的 contentList 同源, 否则内容 md5 对不上, 标注缓存永不命中
        assertTrue(
            "预取段落表与起播段落表不同源",
            preDownload.contains("getNeedReadAloud(0, readAloudByPage, 0)")
        )
        assertTrue("排版未完成时页表不全", preDownload.contains("!nextChapter.isCompleted"))
    }

    @Test
    fun `the analysing state reaches the notification and always clears`() {
        assertTrue("缺分析态开关", base.contains("fun upAnalyzingRoles(analyzing: Boolean)"))
        assertTrue("通知未显示分析态", base.contains("R.string.role_analyzing"))
        assertTrue("分析态未点亮", prepareScript.contains("upAnalyzingRoles(AppConfig.multiRoleReadAloud)"))
        // 失败与取消都要落回常态, 收尾必须挂 finally
        assertTrue("分析态未走 finally 收尾", prepareScript.contains("finally"))
        assertTrue("分析态未收尾", prepareScript.contains("upAnalyzingRoles(false)"))
    }
}
