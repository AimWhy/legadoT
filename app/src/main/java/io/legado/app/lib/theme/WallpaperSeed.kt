package io.legado.app.lib.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ThemeConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean

/**
 * N4 主题 UX:壁纸动态色跟随(Android 12+)。
 *
 * 取系统壁纸主色作种子,交 [ThemeSeedApplier.applySeed] 派生四色(mode="wallpaper");
 * 开启"变化自动更新"则注册壁纸颜色变化监听,壁纸换了自动重新取色。
 *
 * 契约:
 * - [isAvailable]:仅 SDK>=31(S)可用——壁纸取色 API 12+ 才有,入口按此显隐。
 * - [currentSeed]:取不到(<31 或系统无壁纸主色)回 null。
 * - [setFollow]`(true)`:currentSeed 为 null 直接回 false(调用方 toast+回滚开关,不改任何状态);
 *   否则 applySeed→autoUpdate 时注册监听;回 true。
 * - [setFollow]`(false)`:注销监听 + themeSeedMode="";四色留末次派生值,不回滚。
 *
 * 监听单实例:监听器实例存于本 object(App 级单例,跨 Activity 存活),
 * 注册前先注销旧实例([listener]!=null 即先移除),保证进程内至多一个监听。
 * 回调内以 [ThemeConfig.themeSeedMode]=="wallpaper" 守卫——用户切到预设/手动后,
 * 残留监听(理论上已注销,双保险)不会把颜色拉回壁纸色。
 */
object WallpaperSeed {

    /**
     * App 级监听单实例;非 null 表示当前已注册,是防重注册的唯一判据。
     * 类型故意存为 [Any]:[WallpaperManager.OnColorsChangedListener] 本身是 API 27+ 类型,
     * 而本 object 在 App.onCreate 于所有 API 等级(minSdk 26)无条件加载——采用标准
     * Any?-holder 惯例,不在无条件加载的类里让高版本专属类型出现在字段签名中,只在
     * [registerListener]/[unregisterListener](均 @RequiresApi(S) 且外层已做 SDK_INT 门)
     * 内部按需强转。
     */
    @Volatile
    private var listener: Any? = null

    /**
     * 防 RECREATE 风暴:记录上次成功应用的种子值。壁纸变化监听可能高频触发(系统无dedup),
     * 每次 applySeed 引发 8 次持久化写 + 全局 RECREATE;等值守卫防冗余更新。
     */
    @Volatile
    private var lastAppliedSeed: Int? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    /** 壁纸取色仅 12+(S=31)可用。 */
    fun isAvailable(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** 取系统壁纸主色作种子;<31 或无壁纸主色回 null。 */
    fun currentSeed(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return readWallpaperSeed(context)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun readWallpaperSeed(context: Context): Int? {
        return try {
            WallpaperManager.getInstance(context)
                .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                ?.primaryColor
                ?.toArgb()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 开/关壁纸跟随。
     * @return 开启且成功=true;开启但取不到种子=false(调用方负责 toast+回滚开关);关闭恒 true。
     */
    fun setFollow(context: Context, enabled: Boolean, autoUpdate: Boolean): Boolean {
        if (!enabled) {
            // 关闭路径 pre-S 也可达(如备份恢复带来的残留 pref)——调用点补 SDK 门,
            // 与 unregisterListener 的 @RequiresApi(S) 注解配对(否则 NewApi lint 违规)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                unregisterListener(context)
            }
            context.putPrefBoolean(PreferKey.wallpaperFollow, false)
            // 四色留末次派生值,不回滚;仅撤销"种子来源=壁纸"标记,后续手动改色/预设可正常接管。
            ThemeConfig.themeSeedMode = ""
            lastAppliedSeed = null  // 清理状态,为下次启用做准备。
            return true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val seed = currentSeed(context) ?: return false
        context.putPrefBoolean(PreferKey.wallpaperFollow, true)
        context.putPrefBoolean(PreferKey.wallpaperAutoUpdate, autoUpdate)
        ThemeSeedApplier.applySeed(context, seed, "wallpaper")
        lastAppliedSeed = seed  // 记录初始应用的种子值,防止同值重复更新。
        if (autoUpdate) {
            registerListener(context)
        } else {
            unregisterListener(context)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @MainThread
    private fun registerListener(context: Context) {
        // 防重注册:已有实例先注销,进程内至多一个监听。
        // @MainThread 约束 + @Volatile 保证 listener 可见性,不需额外同步。
        if (listener != null) return
        val appContext = context.applicationContext
        val l = WallpaperManager.OnColorsChangedListener { colors, which ->
            // 守卫:用户切走后残留回调不得把颜色拉回壁纸色。
            if (ThemeConfig.themeSeedMode != "wallpaper") return@OnColorsChangedListener
            if (colors == null) return@OnColorsChangedListener
            if (which and WallpaperManager.FLAG_SYSTEM == 0) return@OnColorsChangedListener
            val seed = colors.primaryColor.toArgb()
            // 防 RECREATE 风暴:等值守卫,壁纸快速变化回调(系统无dedup)时避免冗余 applySeed。
            if (seed == lastAppliedSeed) return@OnColorsChangedListener
            // applySeed 内含 postEvent(RECREATE),须在主线程——监听已用 mainHandler 派发,天然满足。
            ThemeSeedApplier.applySeed(appContext, seed, "wallpaper")
            lastAppliedSeed = seed
        }
        WallpaperManager.getInstance(appContext)
            .addOnColorsChangedListener(l, mainHandler)
        listener = l
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @MainThread
    private fun unregisterListener(context: Context) {
        val l = listener ?: return
        try {
            @Suppress("UNCHECKED_CAST")
            WallpaperManager.getInstance(context.applicationContext)
                .removeOnColorsChangedListener(l as WallpaperManager.OnColorsChangedListener)
        } catch (_: Exception) {
        }
        listener = null
    }

    /**
     * 冷启动恢复:若上次开启了壁纸跟随+自动更新,重新挂上监听。
     * App.onCreate 主线程调用(applySeed 走既有落地管线,冷启动的四色已由持久化的 8 键生效,
     * 此处只补挂监听,不重复 applySeed 以免多余 RECREATE)。
     */
    fun restoreListenerIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!context.getPrefBoolean(PreferKey.wallpaperFollow, false)) return
        if (!context.getPrefBoolean(PreferKey.wallpaperAutoUpdate, false)) return
        if (ThemeConfig.themeSeedMode != "wallpaper") return
        registerListener(context)
    }
}
