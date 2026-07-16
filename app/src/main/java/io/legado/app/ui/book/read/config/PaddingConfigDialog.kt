package io.legado.app.ui.book.read.config

import android.animation.ValueAnimator
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.widget.TooltipCompat
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogReadPaddingBinding
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.TrailingThrottle
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.postEvent
import io.legado.app.utils.viewbindingdelegate.viewBinding

class PaddingConfigDialog : BaseDialogFragment(R.layout.dialog_read_padding) {

    /** 贴底面板自设背景(bottomBackground),豁免统一圆角模板 */
    override val dialogForm = DialogForm.SELF_MANAGED

    private val binding by viewBinding(DialogReadPaddingBinding::bind)
    private val defaultConfig = ReadBookConfig.Config()
    private var curRegion = Region.BODY
    private var lockLR = false
    private var bindingRegion = false
    private var textColor = 0
    private var bgColor = 0
    private var bodyThrottle: TrailingThrottle? = null
    private var alphaAnimator: ValueAnimator? = null

    private enum class Region(val titleRes: Int) {
        HEADER(R.string.header),
        BODY(R.string.main_body),
        FOOTER(R.string.footer),
    }

    private enum class Side { TOP, BOTTOM, LEFT, RIGHT }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            val attr = attributes
            attr.dimAmount = 0.0f
            attr.gravity = Gravity.BOTTOM
            attributes = attr
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as ReadBookActivity).bottomDialog++
        bodyThrottle = TrailingThrottle(
            150,
            { r, delay -> binding.root.postDelayed(r, delay) },
            { r -> binding.root.removeCallbacks(r) },
        )
        initView()
        bindRegion(Region.BODY)
    }

    override fun onDestroyView() {
        bodyThrottle?.cancel()
        alphaAnimator?.cancel()
        super.onDestroyView()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        ReadBookConfig.save()
        (activity as ReadBookActivity).bottomDialog--
    }

    private fun initView() = binding.run {
        bgColor = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bgColor)
        textColor = requireContext().getPrimaryTextColor(isLight)
        val radius = requireContext().resources.getDimension(R.dimen.radius_l)
        rootView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
            setColor(bgColor)
        }
        swShowLine.setTextColor(textColor)
        ivReset.setColorFilter(textColor)
        TooltipCompat.setTooltipText(ivReset, getString(R.string.restore_default))
        TooltipCompat.setTooltipText(ivLockLr, getString(R.string.lock_left_right_padding))

        arrayOf(dsbTop, dsbBottom, dsbLeft, dsbRight).forEach { dsb ->
            dsb.onTrackingStart = { ghostWindow(true) }
            dsb.onTrackingStop = { ghostWindow(false) }
        }
        dsbTop.onDragging = { liveApply(Side.TOP, it) }
        dsbBottom.onDragging = { liveApply(Side.BOTTOM, it) }
        dsbLeft.onDragging = { liveApply(Side.LEFT, it) }
        dsbRight.onDragging = { liveApply(Side.RIGHT, it) }
        dsbTop.onChanged = { finalApply(Side.TOP, it) }
        dsbBottom.onChanged = { finalApply(Side.BOTTOM, it) }
        dsbLeft.onChanged = { finalApply(Side.LEFT, it) }
        dsbRight.onChanged = { finalApply(Side.RIGHT, it) }

        btnRegionHeader.setOnClickListener { bindRegion(Region.HEADER) }
        btnRegionBody.setOnClickListener { bindRegion(Region.BODY) }
        btnRegionFooter.setOnClickListener { bindRegion(Region.FOOTER) }
        ivLockLr.setOnClickListener {
            lockLR = !lockLR
            upLockIcon()
        }
        ivReset.setOnClickListener { askResetRegion() }
        swShowLine.setOnCheckedChangeListener { _, isChecked ->
            if (bindingRegion) return@setOnCheckedChangeListener
            when (curRegion) {
                Region.HEADER -> ReadBookConfig.showHeaderLine = isChecked
                Region.FOOTER -> ReadBookConfig.showFooterLine = isChecked
                Region.BODY -> return@setOnCheckedChangeListener
            }
            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
        }
    }

    private fun bindRegion(region: Region) = binding.run {
        // 切分段先清挂起节流写,防旧分段的拖动值越区落入新分段字段
        bodyThrottle?.cancel()
        bindingRegion = true
        curRegion = region
        // 先 max 后 progress:max setter 会把现值收缩进新上限
        val topBottomMax = if (region == Region.BODY) 400 else 100
        dsbTop.max = topBottomMax
        dsbBottom.max = topBottomMax
        dsbLeft.max = 100
        dsbRight.max = 100
        dsbTop.progress = getPadding(region, Side.TOP)
        dsbBottom.progress = getPadding(region, Side.BOTTOM)
        dsbLeft.progress = getPadding(region, Side.LEFT)
        dsbRight.progress = getPadding(region, Side.RIGHT)
        when (region) {
            // INVISIBLE 占位保持三分段内容区等高,切换不跳动
            Region.BODY -> swShowLine.visibility = View.INVISIBLE
            Region.HEADER -> {
                swShowLine.visibility = View.VISIBLE
                swShowLine.isChecked = ReadBookConfig.showHeaderLine
            }
            Region.FOOTER -> {
                swShowLine.visibility = View.VISIBLE
                swShowLine.isChecked = ReadBookConfig.showFooterLine
            }
        }
        lockLR = getPadding(region, Side.LEFT) == getPadding(region, Side.RIGHT)
        upLockIcon()
        upChipStyles()
        bindingRegion = false
    }

    private fun upChipStyles() = binding.run {
        mapOf(
            Region.HEADER to btnRegionHeader,
            Region.BODY to btnRegionBody,
            Region.FOOTER to btnRegionFooter,
        ).forEach { (region, btn) ->
            btn.strokeColor = ColorStateList.valueOf(textColor)
            btn.rippleColor =
                ColorStateList.valueOf(requireContext().getCompatColor(R.color.transparent30))
            if (region == curRegion) {
                btn.backgroundTintList = ColorStateList.valueOf(textColor)
                btn.setTextColor(bgColor)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
                btn.setTextColor(textColor)
            }
        }
    }

    private fun upLockIcon() = binding.ivLockLr.run {
        setImageResource(if (lockLR) R.drawable.ic_link_24dp else R.drawable.ic_link_off_24dp)
        setColorFilter(textColor)
        alpha = if (lockLR) 1f else 0.5f
    }

    private fun liveApply(side: Side, value: Int) {
        if (curRegion == Region.BODY) {
            // 正文改动触发整章重排版,拖动中尾沿节流
            bodyThrottle?.submit { applySide(side, value) }
        } else {
            applySide(side, value)
        }
    }

    private fun finalApply(side: Side, value: Int) {
        bodyThrottle?.cancel()
        applySide(side, value)
    }

    private fun applySide(side: Side, value: Int) {
        setPadding(curRegion, side, value)
        if (lockLR && (side == Side.LEFT || side == Side.RIGHT)) {
            val other = if (side == Side.LEFT) Side.RIGHT else Side.LEFT
            setPadding(curRegion, other, value)
            val otherBar = if (other == Side.LEFT) binding.dsbLeft else binding.dsbRight
            if (otherBar.progress != value) otherBar.progress = value
        }
        postRegionEvent()
    }

    private fun postRegionEvent() {
        postEvent(
            EventBus.UP_CONFIG,
            if (curRegion == Region.BODY) arrayListOf(10, 5) else arrayListOf(2)
        )
    }

    /** 拖动中窗口降透明看穿面板,松手恢复 */
    private fun ghostWindow(ghost: Boolean) {
        val window = dialog?.window ?: return
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofFloat(window.attributes.alpha, if (ghost) 0.25f else 1f)
            .apply {
                duration = 120
                addUpdateListener { anim ->
                    val attr = window.attributes
                    attr.alpha = anim.animatedValue as Float
                    window.attributes = attr
                }
                start()
            }
    }

    private fun askResetRegion() {
        alert(R.string.restore_default) {
            setMessage(
                getString(R.string.reset_region_padding_confirm, getString(curRegion.titleRes))
            )
            yesButton {
                Side.entries.forEach { side ->
                    setPadding(curRegion, side, defaultPadding(curRegion, side))
                }
                bindRegion(curRegion)
                postRegionEvent()
            }
            noButton()
        }
    }

    private fun getPadding(region: Region, side: Side): Int = when (region) {
        Region.HEADER -> when (side) {
            Side.TOP -> ReadBookConfig.headerPaddingTop
            Side.BOTTOM -> ReadBookConfig.headerPaddingBottom
            Side.LEFT -> ReadBookConfig.headerPaddingLeft
            Side.RIGHT -> ReadBookConfig.headerPaddingRight
        }
        Region.BODY -> when (side) {
            Side.TOP -> ReadBookConfig.paddingTop
            Side.BOTTOM -> ReadBookConfig.paddingBottom
            Side.LEFT -> ReadBookConfig.paddingLeft
            Side.RIGHT -> ReadBookConfig.paddingRight
        }
        Region.FOOTER -> when (side) {
            Side.TOP -> ReadBookConfig.footerPaddingTop
            Side.BOTTOM -> ReadBookConfig.footerPaddingBottom
            Side.LEFT -> ReadBookConfig.footerPaddingLeft
            Side.RIGHT -> ReadBookConfig.footerPaddingRight
        }
    }

    private fun setPadding(region: Region, side: Side, value: Int) {
        when (region) {
            Region.HEADER -> when (side) {
                Side.TOP -> ReadBookConfig.headerPaddingTop = value
                Side.BOTTOM -> ReadBookConfig.headerPaddingBottom = value
                Side.LEFT -> ReadBookConfig.headerPaddingLeft = value
                Side.RIGHT -> ReadBookConfig.headerPaddingRight = value
            }
            Region.BODY -> when (side) {
                Side.TOP -> ReadBookConfig.paddingTop = value
                Side.BOTTOM -> ReadBookConfig.paddingBottom = value
                Side.LEFT -> ReadBookConfig.paddingLeft = value
                Side.RIGHT -> ReadBookConfig.paddingRight = value
            }
            Region.FOOTER -> when (side) {
                Side.TOP -> ReadBookConfig.footerPaddingTop = value
                Side.BOTTOM -> ReadBookConfig.footerPaddingBottom = value
                Side.LEFT -> ReadBookConfig.footerPaddingLeft = value
                Side.RIGHT -> ReadBookConfig.footerPaddingRight = value
            }
        }
    }

    private fun defaultPadding(region: Region, side: Side): Int = when (region) {
        Region.HEADER -> when (side) {
            Side.TOP -> defaultConfig.headerPaddingTop
            Side.BOTTOM -> defaultConfig.headerPaddingBottom
            Side.LEFT -> defaultConfig.headerPaddingLeft
            Side.RIGHT -> defaultConfig.headerPaddingRight
        }
        Region.BODY -> when (side) {
            Side.TOP -> defaultConfig.paddingTop
            Side.BOTTOM -> defaultConfig.paddingBottom
            Side.LEFT -> defaultConfig.paddingLeft
            Side.RIGHT -> defaultConfig.paddingRight
        }
        Region.FOOTER -> when (side) {
            Side.TOP -> defaultConfig.footerPaddingTop
            Side.BOTTOM -> defaultConfig.footerPaddingBottom
            Side.LEFT -> defaultConfig.footerPaddingLeft
            Side.RIGHT -> defaultConfig.footerPaddingRight
        }
    }
}
