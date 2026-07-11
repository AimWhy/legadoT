package io.legado.app.lib.theme

import android.content.Context
import androidx.annotation.ColorInt
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ThemeConfig
import io.legado.app.utils.putPrefInt

/**
 * N4 主题 UX 种子写入器:预设主题(preset:<id>)与壁纸取色(wallpaper)共用的唯一四色写入点,
 * 避免两个消费方各自重复"种子→四色"的计算与落盘逻辑。
 *
 * 种子色→按 [AppColorScheme.ambientScheme] 派生日/夜两组完整色板→写日夜 8 键→
 * 记录 [ThemeConfig.themeSeedMode]→交给既有 [ThemeConfig.applyDayNight] 落地
 * (内部会走 applyTheme + RECREATE,与手动改色的既有管线完全一致)。
 *
 * 红线:不得绕过 [ThemeConfig.applyTheme] 直写 ThemeStore——eink 用户的黑白覆盖分支
 * (ThemeConfig.applyTheme 的 AppConfig.isEInkMode 分支)必须继续生效。该分支只认
 * AppConfig.isEInkMode,与此处写入的 8 键取值无关,因此这里永远按非 eink 派生颜色
 * (isEInk=false 显式传参,不用默认参数——纯 JVM 环境下 ambientScheme 的默认参数会
 * 触发 AppConfig 的安卓初始化,单测会炸)。
 */
object ThemeSeedApplier {

    fun applySeed(context: Context, @ColorInt seed: Int, mode: String) {
        val day = AppColorScheme.ambientScheme(seed, isDark = false, isEInk = false)
        val night = AppColorScheme.ambientScheme(seed, isDark = true, isEInk = false)

        context.putPrefInt(PreferKey.cPrimary, day.surfaceContainer)
        context.putPrefInt(PreferKey.cAccent, day.primary)
        context.putPrefInt(PreferKey.cBackground, day.background)
        context.putPrefInt(PreferKey.cBBackground, day.surfaceContainer)

        context.putPrefInt(PreferKey.cNPrimary, night.surfaceContainer)
        context.putPrefInt(PreferKey.cNAccent, night.primary)
        context.putPrefInt(PreferKey.cNBackground, night.background)
        context.putPrefInt(PreferKey.cNBBackground, night.surfaceContainer)

        // 状态分叉修复:非壁纸来源(预设/手动)落盘前先拆除可能仍开着的壁纸跟随机关,
        // 否则残留的 wallpaperFollow=true 会让用户之后翻转 wallpaperAutoUpdate 子开关时
        // 把这里刚写的种子色拽回壁纸色。必须在写 themeSeedMode 之前调用——
        // abandonFollowIfActive 本身不碰 themeSeedMode,由本函数紧接着的下一行统一写入。
        if (mode != "wallpaper") {
            WallpaperSeed.abandonFollowIfActive(context)
        }

        ThemeConfig.themeSeedMode = mode

        ThemeConfig.applyDayNight(context)
    }
}
