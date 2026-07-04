package io.legado.app.lib.skin

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.LayoutInflaterCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.radiobutton.MaterialRadioButton
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.view.ThemeCheckBox
import io.legado.app.lib.theme.view.ThemeEditText
import io.legado.app.lib.theme.view.ThemeRadioButton
import io.legado.app.lib.theme.view.ThemeSwitch
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyTint
import java.lang.reflect.Constructor

/**
 * R2 换肤引擎核心:LayoutInflater.Factory2。
 *
 * 安装:BaseActivity 在 super.onCreate 前经 [install] 抢占工厂位;AppCompat 发现已有工厂会让位,
 * 视图创建仍委托 [AppCompatDelegate.createView] 保留其兼容替换(TextView→AppCompatTextView 等)。
 * 弹窗/菜单/列表 item 的 inflater 均由 activity inflater 经 cloneInContext 派生(工厂随克隆继承),无需二次安装。
 *
 * 规则 A(类型默认):标准表单控件与 Theme* 行为壳自动施强调色(施色值移植自原 Theme* 族,
 * R2b 起 Theme* 只剩行为、施色单轨归此)。精确按 javaClass 匹配——其余自定义子类继续自治。
 *
 * 规则 B(显式角色):布局声明 app:skin_background / skin_textColor / skin_tint / skin_hintColor,
 * 取值为 [SkinRole] 角色名,inflate 时从 [AppColorScheme] 取当前色一次性施加
 * (主题切换走 RECREATE 整体重建,无需动态刷新)。
 */
class SkinInflaterFactory(activity: AppCompatActivity) : LayoutInflater.Factory2 {

    private val delegate: AppCompatDelegate = activity.delegate
    private val constructorCache = HashMap<String, Constructor<out View>?>()

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        // fragment 族有专属装配线;菜单行保留 BaseActivity 私有工厂的置底色逻辑——均交还系统路径
        if (name in SKIP_NAMES) return null
        // TypedArray 经 obtainStyledAttributes 解析,style= 携带的 skin_* 同样命中(卡片族走样式声明)
        val ta = context.obtainStyledAttributes(attrs, R.styleable.SkinView)
        try {
            val hasSkin = ta.indexCount > 0
            val view = delegate.createView(parent, name, context, attrs)
                ?: (if (name in RULE_A_FALLBACK_NAMES || hasSkin) {
                    createFallbackView(name, context, attrs)
                } else null)
                ?: return null
            applyTypeDefaults(view)
            if (hasSkin) applySkinAttrs(view, ta)
            return view
        } finally {
            ta.recycle()
        }
    }

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? =
        onCreateView(null, name, context, attrs)

    /**
     * AppCompat 不接管的名字(表单全名/ProgressBar/带 skin 属性的容器与自定义控件):
     * 反射创建以获得实例施色;其余交还系统原路径(返回 null,行为与装引擎前逐字节一致)。
     * android:theme 包装比照 LayoutInflater 原生行为(反射前包 ContextThemeWrapper)。
     */
    private fun createFallbackView(name: String, context: Context, attrs: AttributeSet): View? {
        val constructor = constructorCache.getOrPut(name) {
            runCatching {
                val clazz = if ('.' in name) {
                    context.classLoader.loadClass(name)
                } else {
                    CLASS_PREFIXES.firstNotNullOfOrNull { prefix ->
                        runCatching { context.classLoader.loadClass(prefix + name) }.getOrNull()
                    }
                }
                clazz?.asSubclass(View::class.java)
                    ?.getConstructor(Context::class.java, AttributeSet::class.java)
            }.getOrNull()
        }
        // android:theme 可能是 ?attr/ 引用,须经 obtainStyledAttributes 解析(比照 LayoutInflater 原生行为)
        val themeTa = context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.theme))
        val themeResId = try {
            themeTa.getResourceId(0, 0)
        } finally {
            themeTa.recycle()
        }
        val viewContext = if (themeResId != 0) {
            androidx.appcompat.view.ContextThemeWrapper(context, themeResId)
        } else context
        return runCatching { constructor?.newInstance(viewContext, attrs) }.getOrNull()
    }

    /**
     * 规则 A:精确类匹配施默认强调色(施色值移植自原 Theme* 族)。
     * Theme* 行为壳(R2b 瘦身后仅剩 isUserAction/粘贴防护等行为)的施色也由此接管。
     */
    private fun applyTypeDefaults(view: View) {
        when (view.javaClass) {
            AppCompatCheckBox::class.java, MaterialCheckBox::class.java,
            AppCompatRadioButton::class.java, MaterialRadioButton::class.java,
            ThemeCheckBox::class.java, ThemeRadioButton::class.java,
                -> (view as CompoundButton).buttonTintList = checkableTint(view.context)

            // 仅 M3 开关:SwitchCompat/SwitchMaterial 是 M2 解剖,套 M3 白拇指配色会变样,
            // 零星存量(dialog_simulated_reading)留 R2c 换标签后自然入网
            MaterialSwitch::class.java, ThemeSwitch::class.java,
                -> applySwitchTint(view as SwitchCompat)

            AppCompatSeekBar::class.java, ProgressBar::class.java,
            AppCompatEditText::class.java, ThemeEditText::class.java,
                -> view.applyTint(view.context.accentColor)
        }
    }

    /** ThemeCheckBox/ThemeRadioButton 同款:选中=accent,未选=60% 灰 */
    private fun checkableTint(context: Context) = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
        intArrayOf(context.accentColor, ColorUtils.withAlpha(0x888888, 0.6f))
    )

    /** ThemeSwitch 同款:track 选中=accent/未选 32% 灰,thumb 白/灰白 */
    private fun applySwitchTint(switch: SwitchCompat) {
        val checkedStates = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        switch.trackTintList = ColorStateList(
            checkedStates,
            intArrayOf(switch.context.accentColor, ColorUtils.withAlpha(0x888888, 0.32f))
        )
        switch.thumbTintList = ColorStateList(
            checkedStates,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFBDBDBD.toInt())
        )
    }

    /** 规则 B:读 skin_* 角色声明施色(TypedArray 生命周期由调用方管理) */
    private fun applySkinAttrs(view: View, ta: android.content.res.TypedArray) {
        val scheme = AppColorScheme.current
        readRole(ta, R.styleable.SkinView_skin_background)?.resolve(scheme)?.let { color ->
            // 卡片背景走专用通道,普通视图平涂(等价 android:background="@color/x")
            if (view is MaterialCardView) view.setCardBackgroundColor(color)
            else view.setBackgroundColor(color)
        }
        readRole(ta, R.styleable.SkinView_skin_textColor)?.resolve(scheme)?.let {
            (view as? TextView)?.setTextColor(it)
        }
        readRole(ta, R.styleable.SkinView_skin_hintColor)?.resolve(scheme)?.let {
            (view as? TextView)?.setHintTextColor(it)
        }
        readRole(ta, R.styleable.SkinView_skin_tint)?.resolve(scheme)?.let { color ->
            when (view) {
                is ImageView -> view.imageTintList = ColorStateList.valueOf(color)
                is ProgressBar -> view.applyTint(color)
                else -> view.backgroundTintList = ColorStateList.valueOf(color)
            }
        }
    }

    private fun readRole(ta: android.content.res.TypedArray, index: Int): SkinRole? {
        val value = ta.getInt(index, -1)
        return if (value < 0) null else SkinRole.fromAttrValue(value)
    }

    companion object {

        /**
         * 交还系统私有工厂处理的标签(fragment 族有专属装配线,菜单行保留 BaseActivity 置底色逻辑)。
         * Toolbar 曾防御性跳过,R2c 解禁:BaseActivity.onCreateView 的 `view is Toolbar` 分支
         * 经查为死代码(Activity/FragmentActivity 的 super.onCreateView 对非 fragment 标签恒返 null;
         * activity 溢出菜单走 onCreateOptionsMenu 显式安装,弹窗 toolbar 走 MD3 弹出样式)。
         */
        private val SKIP_NAMES = hashSetOf(
            "fragment",
            "androidx.fragment.app.FragmentContainerView",
            *AppConst.menuViewNames,
        )

        /** 系统 LayoutInflater 对无点名字的前缀尝试序 */
        private val CLASS_PREFIXES = arrayOf(
            "android.widget.", "android.view.", "android.webkit.", "android.app.",
        )

        /** AppCompat 不创建、但规则 A 需要实例的名字 */
        private val RULE_A_FALLBACK_NAMES = hashSetOf(
            "ProgressBar",
            "com.google.android.material.materialswitch.MaterialSwitch",
            "com.google.android.material.checkbox.MaterialCheckBox",
            "com.google.android.material.radiobutton.MaterialRadioButton",
            "androidx.appcompat.widget.AppCompatCheckBox",
            "androidx.appcompat.widget.AppCompatRadioButton",
            "androidx.appcompat.widget.AppCompatSeekBar",
            "androidx.appcompat.widget.AppCompatEditText",
            // Theme* 行为壳(R2b 瘦身,施色归引擎)
            "io.legado.app.lib.theme.view.ThemeCheckBox",
            "io.legado.app.lib.theme.view.ThemeSwitch",
            "io.legado.app.lib.theme.view.ThemeRadioButton",
            "io.legado.app.lib.theme.view.ThemeEditText",
        )

        /**
         * 在 super.onCreate 前安装(须早于 AppCompat 的工厂安装与首次 inflate)。
         * AppCompat 检测到已有工厂会跳过自装并打一条 INFO 日志,属预期。
         */
        fun install(activity: AppCompatActivity) {
            installOn(activity.layoutInflater, activity)
        }

        /**
         * 弹窗通路保险:Dialog 窗口 inflater 通常由 activity inflater 经 cloneInContext
         * 派生(克隆构造复制 factory2,正常已继承,此处空转);个别 OEM 魔改返回全新
         * inflater 时在此补装。已有工厂时静默跳过(setFactory2 对已设工厂会抛异常)。
         */
        fun installOn(inflater: LayoutInflater, activity: AppCompatActivity) {
            if (inflater.factory2 == null) {
                LayoutInflaterCompat.setFactory2(inflater, SkinInflaterFactory(activity))
            }
        }
    }
}
