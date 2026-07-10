package io.legado.app.lib.prefs

import android.content.Context
import android.content.ContextWrapper
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceViewHolder
import io.legado.app.R
import io.legado.app.ui.widget.dialog.M3ColorPickerDialog
import io.legado.app.ui.widget.image.CircleImageView
import io.legado.app.utils.ColorUtils

@Suppress("MemberVisibilityCanBePrivate", "unused")
class ColorPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    var onSaveColor: ((color: Int) -> Boolean)? = null

    private var onShowDialogListener: OnShowDialogListener? = null
    private var mColor = Color.BLACK
    private var showDialog: Boolean = false

    private var showAlphaSlider: Boolean = false
    private var presets: IntArray? = null

    init {
        isPersistent = true
        layoutResource = R.layout.view_preference
        widgetLayoutResource = R.layout.view_color_preference_preview

        val a = context.obtainStyledAttributes(attrs, R.styleable.ColorPreference)
        showDialog = a.getBoolean(R.styleable.ColorPreference_cpv_showDialog, true)
        showAlphaSlider = a.getBoolean(R.styleable.ColorPreference_cpv_showAlphaSlider, false)
        a.recycle()
        presets = MATERIAL_COLORS
    }

    override fun onClick() {
        super.onClick()
        if (onShowDialogListener != null) {
            onShowDialogListener!!.onShowColorPickerDialog(title as String, mColor)
        } else if (showDialog) {
            M3ColorPickerDialog.show(
                getActivity().supportFragmentManager,
                getFragmentTag(),
                mColor,
                showAlphaSlider,
                presets
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

    override fun onAttached() {
        super.onAttached()
        if (showDialog) {
            // ColorPreference 不是 Fragment/Activity,自身没有生命周期感知的注册时机；
            // 借宿主 Activity 的 supportFragmentManager 在 onAttached (每次 Preference 重新可见都会走)
            // 重新注册,天然覆盖旋转重建场景,无需额外的 onSaveInstanceState 记录。
            getActivity().supportFragmentManager.setFragmentResultListener(
                getFragmentTag(), getActivity()
            ) { _, bundle ->
                val color = bundle.getInt(M3ColorPickerDialog.RESULT_COLOR)
                //返回值为true时说明已经处理过,不再处理
                if (onSaveColor?.invoke(color) != true) {
                    saveValue(color)
                }
            }
        }
    }

    override fun onBindView(holder: PreferenceViewHolder) {
        val v = bindView<CircleImageView>(
            context, holder, icon, title, summary, widgetLayoutResource,
            R.id.color_preview, 30, 30
        )
        v?.setImageDrawable(ColorDrawable(mColor))
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        super.onSetInitialValue(defaultValue)
        if (defaultValue is Int) {
            mColor = if (!showAlphaSlider) ColorUtils.withAlpha(defaultValue, 1f) else defaultValue
            persistInt(mColor)
        } else {
            mColor = getPersistedInt(-0x1000000)
        }
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return a.getInteger(index, Color.BLACK)
    }

    /**
     * Set the new color
     *
     * @param color The newly selected color
     */
    fun saveValue(@ColorInt color: Int) {
        mColor = if (showAlphaSlider) color else ColorUtils.withAlpha(color, 1f)
        persistInt(mColor)
        notifyChanged()
        callChangeListener(color)
    }

    /**
     * Get the colors that will be shown in the [M3ColorPickerDialog].
     *
     * @return An array of color ints
     */
    fun getPresets(): IntArray? {
        return presets
    }

    /**
     * Set the colors shown in the [M3ColorPickerDialog].
     *
     * @param presets An array of color ints
     */
    fun setPresets(presets: IntArray) {
        this.presets = presets
    }

    /**
     * The listener used for showing the color picker dialog.
     * Call [.saveValue] after the user chooses a color.
     * If this is set then it is up to you to show the dialog.
     *
     * @param listener The listener to show the dialog
     */
    fun setOnShowDialogListener(listener: OnShowDialogListener) {
        onShowDialogListener = listener
    }

    /**
     * The requestKey used for the color picker dialog's [androidx.fragment.app.FragmentResult].
     *
     * @return The tag
     */
    fun getFragmentTag(): String {
        return "color_$key"
    }

    interface OnShowDialogListener {

        fun onShowColorPickerDialog(title: String, currentColor: Int)
    }

    companion object {
        /** 通用预设色板(原第三方取色器库 MATERIAL_COLORS 等价常用色) */
        val MATERIAL_COLORS = intArrayOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(), 0xFF673AB7.toInt(),
            0xFF3F51B5.toInt(), 0xFF2196F3.toInt(), 0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(),
            0xFF009688.toInt(), 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(), 0xFFFF5722.toInt(),
            0xFF795548.toInt(), 0xFF9E9E9E.toInt(), 0xFF607D8B.toInt(), 0xFF000000.toInt(),
        )
    }
}
