package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import splitties.init.appCtx

/**
 * 用户四色(主色/强调色/背景色/底栏色)的 SharedPreferences 存储层。
 * 写入端只有 [io.legado.app.help.config.ThemeConfig.applyTheme];读取端是取色扩展、
 * [AppColorScheme](种子+锚)与 [io.legado.app.lib.skin.SkinRole] 的 app 四色角色。
 * R2d 已退役 ATH 时代的状态栏/导航栏/文字色等从未写入的键族(getter 恒返默认值,等价直读四色)。
 *
 * @author Aidan Follestad (afollestad), Karim Abou Zeid (kabouzeid)
 */
class ThemeStore @SuppressLint("CommitPrefEdits")
private constructor(mContext: Context) {

    private val mEditor = prefs(mContext).edit()

    fun primaryColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_COLOR, color)
        return this
    }

    fun accentColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_ACCENT_COLOR, color)
        return this
    }

    fun backgroundColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_BACKGROUND_COLOR, color)
        return this
    }

    fun bottomBackground(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_BOTTOM_BACKGROUND, color)
        return this
    }

    fun apply() {
        mEditor.apply()
        accentColor = accentColor()
    }

    companion object {

        /** 阅读器自绘热路径(翻页画笔/朗读高亮)的免 prefs 读缓存,apply() 时刷新 */
        var accentColor = accentColor()

        fun editTheme(context: Context): ThemeStore {
            return ThemeStore(context)
        }

        @CheckResult
        internal fun prefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(
                ThemeStorePrefKeys.CONFIG_PREFS_KEY_DEFAULT,
                Context.MODE_PRIVATE
            )
        }

        @CheckResult
        @ColorInt
        fun primaryColor(context: Context = appCtx): Int {
            return prefs(context).getInt(
                ThemeStorePrefKeys.KEY_PRIMARY_COLOR,
                ThemeUtils.resolveColor(
                    context,
                    androidx.appcompat.R.attr.colorPrimary,
                    Color.parseColor("#455A64")
                )
            )
        }

        @CheckResult
        @ColorInt
        fun accentColor(context: Context = appCtx): Int {
            return prefs(context).getInt(
                ThemeStorePrefKeys.KEY_ACCENT_COLOR,
                ThemeUtils.resolveColor(
                    context,
                    androidx.appcompat.R.attr.colorAccent,
                    Color.parseColor("#263238")
                )
            )
        }

        @CheckResult
        @ColorInt
        fun backgroundColor(context: Context = appCtx): Int {
            return prefs(context).getInt(
                ThemeStorePrefKeys.KEY_BACKGROUND_COLOR,
                ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
            )
        }

        @CheckResult
        @ColorInt
        fun bottomBackground(context: Context = appCtx): Int {
            return prefs(context).getInt(
                ThemeStorePrefKeys.KEY_BOTTOM_BACKGROUND,
                ThemeUtils.resolveColor(context, android.R.attr.colorBackground)
            )
        }
    }
}
