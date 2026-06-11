package io.legado.app.ui.main.explore.manage

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.databinding.DialogExploreContainerEditBinding
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 容器编辑对话框(管理页点行 / 发现页长按菜单共用)
 */
class ExploreContainerEditDialog : BaseDialogFragment(R.layout.dialog_explore_container_edit, true) {

    companion object {
        fun edit(id: Long) = ExploreContainerEditDialog().apply {
            arguments = Bundle().apply { putLong("id", id) }
        }
    }

    private val binding by viewBinding(DialogExploreContainerEditBinding::bind)
    private var container: ExploreContainer? = null

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvCancel.setOnClickListener { dismiss() }
        binding.tvOk.setOnClickListener { save() }
        binding.tvDelete.setOnClickListener { delete() }
        val id = arguments?.getLong("id", -1) ?: -1
        viewLifecycleOwner.lifecycleScope.launch {
            val c = withContext(IO) { appDb.exploreContainerDao.getById(id) }
            if (c == null) {
                toastOnUi(R.string.explore_source_not_found)
                dismiss()
                return@launch
            }
            container = c
            upView(c)
        }
    }

    private fun upView(c: ExploreContainer) = binding.run {
        tvSourceInfo.text = "${c.sourceName} · ${c.kindTitle}"
        etTitle.setText(c.customTitle)
        if (c.style == ExploreContainer.STYLE_LIST) {
            rbList.isChecked = true
        } else {
            rbFlow.isChecked = true
        }
        etCount.setText(c.listCount.toString())
    }

    private fun save() {
        val c = container ?: return
        c.customTitle = binding.etTitle.text?.toString()?.takeUnless { it.isBlank() }
        c.style = if (binding.rbList.isChecked) {
            ExploreContainer.STYLE_LIST
        } else {
            ExploreContainer.STYLE_FLOW
        }
        c.listCount = binding.etCount.text?.toString()?.toIntOrNull()?.coerceIn(1, 20) ?: 3
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(IO) { appDb.exploreContainerDao.update(c) }
            dismiss()
        }
    }

    private fun delete() {
        val c = container ?: return
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + c.getDisplayTitle())
            noButton()
            yesButton {
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(IO) {
                        appDb.exploreContainerDao.delete(c)
                        ExploreContainerHelp.removeCache(c.id)
                    }
                    dismiss()
                }
            }
        }
    }
}
