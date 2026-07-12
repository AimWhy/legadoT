package io.legado.app.ui.book.read

/** 朗读悬浮胶囊显隐纯逻辑(可 JVM 测):朗读运行中 && 已脱离跟随 && 菜单未显示 才显示 */
object ReadAloudBarVisibility {
    fun shouldShow(isRun: Boolean, following: Boolean, menuVisible: Boolean): Boolean =
        isRun && !following && !menuVisible
}
