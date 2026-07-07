package io.legado.app.ui.widget.code

/** 畸形组合字符文本防护：粘贴 zalgo/组合字符炸弹时降级为只读预览，防排版层假死 */
object EditSafety {

    const val PREVIEW_LINES = 6

    fun isCombiningHeavy(text: String): Boolean {
        if (text.isEmpty()) return false
        var combiningCount = 0
        var inspected = 0
        var run = 0
        var maxRun = 0
        val limit = minOf(text.length, 4000)
        for (i in 0 until limit) {
            val ch = text[i]
            val type = Character.getType(ch)
            val isCombining = type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt()
            if (isCombining) {
                combiningCount++
                run++
                if (run > maxRun) maxRun = run
            } else {
                run = 0
            }
            inspected++
        }
        if (maxRun >= 8) return true
        if (combiningCount >= 64) return true
        return combiningCount * 5 >= inspected
    }
}
