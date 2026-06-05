package io.legado.app.utils

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import io.legado.app.R

fun PopupWindow.applyMd3PopupStyle() {
    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        elevation = 8f.dpToPx()
    }
    if (contentView?.background == null) {
        contentView?.background = ContextCompat.getDrawable(contentView.context, R.drawable.bg_popup_menu)
    }
    isClippingEnabled = true
}
