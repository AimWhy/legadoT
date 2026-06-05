package io.legado.app.utils

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.Toolbar
import androidx.core.view.children
import io.legado.app.ui.widget.PopupAction

fun Toolbar.installMd3OverflowMenu(
    onPrepareMenu: (Menu) -> Unit = {},
    onMenuItemClick: (MenuItem) -> Unit
) {
    updateMd3OverflowMenu(onPrepareMenu, onMenuItemClick)
    setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
        override fun onChildViewAdded(parent: View?, child: View?) {
            updateMd3OverflowMenu(onPrepareMenu, onMenuItemClick)
        }

        override fun onChildViewRemoved(parent: View?, child: View?) {
            updateMd3OverflowMenu(onPrepareMenu, onMenuItemClick)
        }
    })
}

private fun Toolbar.updateMd3OverflowMenu(
    onPrepareMenu: (Menu) -> Unit = {},
    onMenuItemClick: (MenuItem) -> Unit
) {
    post {
        findOverflowAnchor()?.setOnClickListener { anchor ->
            showMd3OverflowMenu(anchor, onPrepareMenu, onMenuItemClick)
        }
    }
}

@SuppressLint("RestrictedApi")
private fun Toolbar.showMd3OverflowMenu(
    anchor: View,
    onPrepareMenu: (Menu) -> Unit,
    onMenuItemClick: (MenuItem) -> Unit
) {
    onPrepareMenu(menu)
    val overflowItems = menu.visibleOverflowItems()
    val actionItems = overflowItems.filter { item ->
        item.subMenu == null && item.actionView == null
    }
    val hasUnsupportedItems = overflowItems.any { item ->
        item.subMenu != null || item.actionView != null
    }
    if (actionItems.isEmpty() || hasUnsupportedItems) {
        showOverflowMenu()
        return
    }

    PopupAction(context).apply {
        setVertical(true)
        setActionItems(
            actionItems.map { item ->
                PopupAction.PopupActionItem(
                    title = item.title?.toString().orEmpty(),
                    value = item.itemId.toString(),
                    icon = item.icon?.constantState?.newDrawable()?.mutate() ?: item.icon,
                    enabled = item.isEnabled,
                    checked = item.isChecked
                )
            }
        )
        onActionClick = { action ->
            dismiss()
            actionItems.firstOrNull { item ->
                item.itemId.toString() == action
            }?.let(onMenuItemClick)
        }
        showAsDropDown(anchor, 0, 4.dpToPx())
    }
}

@SuppressLint("RestrictedApi")
private fun Menu.visibleOverflowItems(): List<MenuItem> {
    val result = arrayListOf<MenuItem>()
    for (index in 0 until size()) {
        val item = getItem(index)
        if (!item.isVisible || !item.isEnabled) {
            continue
        }
        val impl = item as? MenuItemImpl ?: continue
        val belongsInOverflow = impl.requiresOverflow() ||
            (impl.requestsActionButton() && !item.isActionButton)
        if (belongsInOverflow) {
            result.add(item)
        }
    }
    return result
}

private fun View.findOverflowAnchor(): View? {
    if (this is ImageView && javaClass.simpleName.contains("Overflow", ignoreCase = true)) {
        return this
    }
    if (this is ViewGroup) {
        children.forEach { child ->
            child.findOverflowAnchor()?.let { return it }
        }
    }
    return null
}
