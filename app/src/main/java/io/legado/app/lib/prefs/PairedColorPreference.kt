package io.legado.app.lib.prefs

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.util.AttributeSet
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceViewHolder
import com.google.android.material.card.MaterialCardView
import io.legado.app.R
import io.legado.app.lib.theme.AppColorScheme
import io.legado.app.ui.widget.dialog.M3ColorPickerDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt

/**
 * 配对色行:一行一个色彩角色,行尾日/夜两个色块,各自点开取色器写各自的 pref 键。
 *
 * 直接继承 [androidx.preference.Preference](绕过 lib.prefs.Preference 的 bindView 4-id
 * 硬约束,ThemePreviewPreference 先例),自定义布局 [R.layout.view_paired_color_preference]。
 * 行自身不可点(isSelectable=false),色块是独立触控目标。
 *
 * 持久化不走框架单键 persist:两个键经 [Context.putPrefInt] 直写,SharedPreferences 变更由
 * 宿主 Fragment 的监听消费(seedMode 回落/预览刷新/RECREATE 判定)。
 * 取色器结果经 fragmentResult 交付,requestKey 沿用 ColorPreference 的 "color_<prefKey>"
 * 约定;在 onAttached 注册(每次可见重注册,天然覆盖旋转重建)。
 */
class PairedColorPreference(
    context: Context,
    attrs: AttributeSet,
) : androidx.preference.Preference(context, attrs) {

    /** 落盘拦截钩子:返回 true 表示调用方已处理(如校验失败 toast),不写 pref。 */
    var onSaveColor: ((key: String, color: Int) -> Boolean)? = null

    private val dayKey: String
    private val nightKey: String
    private val dayDefault: Int
    private val nightDefault: Int

    init {
        layoutResource = R.layout.view_paired_color_preference
        isSelectable = false
        val a = context.obtainStyledAttributes(attrs, R.styleable.PairedColorPreference)
        dayKey = requireNotNull(a.getString(R.styleable.PairedColorPreference_dayKey)) {
            "PairedColorPreference 必须声明 app:dayKey"
        }
        nightKey = requireNotNull(a.getString(R.styleable.PairedColorPreference_nightKey)) {
            "PairedColorPreference 必须声明 app:nightKey"
        }
        dayDefault = a.getColor(R.styleable.PairedColorPreference_dayDefault, Color.BLACK)
        nightDefault = a.getColor(R.styleable.PairedColorPreference_nightDefault, Color.BLACK)
        a.recycle()
    }

    fun refresh() = notifyChanged()

    override fun onAttached() {
        super.onAttached()
        registerResultListener(dayKey)
        registerResultListener(nightKey)
    }

    private fun registerResultListener(prefKey: String) {
        val activity = getActivity()
        activity.supportFragmentManager.setFragmentResultListener(
            "color_$prefKey", activity
        ) { _, bundle ->
            val color =
                ColorUtils.withAlpha(bundle.getInt(M3ColorPickerDialog.RESULT_COLOR), 1f)
            if (onSaveColor?.invoke(prefKey, color) != true) {
                context.putPrefInt(prefKey, color)
                notifyChanged()
            }
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(R.id.tv_paired_title) as? TextView)?.text = title
        bindSwatch(holder, R.id.card_swatch_day, R.id.tv_swatch_day, dayKey, dayDefault)
        bindSwatch(holder, R.id.card_swatch_night, R.id.tv_swatch_night, nightKey, nightDefault)
    }

    private fun bindSwatch(
        holder: PreferenceViewHolder,
        cardId: Int,
        labelId: Int,
        prefKey: String,
        default: Int,
    ) {
        val card = holder.findViewById(cardId) as? MaterialCardView ?: return
        val label = holder.findViewById(labelId) as? TextView ?: return
        val color = context.getPrefInt(prefKey, default)
        // MaterialCardView 必须走 setCardBackgroundColor,setBackgroundColor 会破坏圆角 shape
        card.setCardBackgroundColor(color)
        card.strokeColor = AppColorScheme.current.outlineVariant
        label.setTextColor(if (ColorUtils.isColorLight(color)) Color.BLACK else Color.WHITE)
        card.setOnClickListener {
            M3ColorPickerDialog.show(
                getActivity().supportFragmentManager,
                "color_$prefKey",
                context.getPrefInt(prefKey, default),
                false,
                ColorPreference.MATERIAL_COLORS,
            )
        }
    }

    private fun getActivity(): FragmentActivity {
        val context = context
        if (context is FragmentActivity) {
            return context
        } else if (context is ContextWrapper) {
            val baseContext = context.baseContext
            if (baseContext is FragmentActivity) {
                return baseContext
            }
        }
        throw IllegalStateException("Error getting activity from context")
    }
}
