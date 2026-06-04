package io.legado.app.lib.theme.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.databinding.ViewNavigationBadgeBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.appBarBackgroundIsLight
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.ui.widget.text.BadgeView

class ThemeBottomNavigationVIew(context: Context, attrs: AttributeSet) :
    BottomNavigationView(context, attrs) {

    init {
        val bgColor = if (AppConfig.isTransparentActionBar) {
            android.graphics.Color.TRANSPARENT
        } else {
            context.bottomBackground
        }
        setBackgroundColor(bgColor)
        val textColor = context.getSecondaryTextColor(
            appBarBackgroundIsLight(
                transparentActionBar = AppConfig.isTransparentActionBar,
                barBackgroundColor = context.bottomBackground,
                contentBackgroundColor = context.backgroundColor
            )
        )
        val colorStateList = Selector.colorBuild()
            .setDefaultColor(textColor)
            .setSelectedColor(context.accentColor).create()
        itemIconTintList = colorStateList
        itemTextColor = colorStateList

        if (AppConfig.isEInkMode) {
            isItemHorizontalTranslationEnabled = false
            itemBackground = ColorDrawable(Color.TRANSPARENT)
        }

        ViewCompat.setOnApplyWindowInsetsListener(this, null)
    }

    fun addBadgeView(index: Int): BadgeView {
        //获取底部菜单view
        val menuView = getChildAt(0) as ViewGroup
        //获取第index个itemView
        val itemView = menuView.getChildAt(index) as ViewGroup
        val badgeBinding = ViewNavigationBadgeBinding.inflate(LayoutInflater.from(context))
        itemView.addView(badgeBinding.root)
        return badgeBinding.viewBadge
    }

}