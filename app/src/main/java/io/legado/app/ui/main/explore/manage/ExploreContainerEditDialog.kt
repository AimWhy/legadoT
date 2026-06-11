package io.legado.app.ui.main.explore.manage

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.databinding.DialogExploreContainerEditBinding
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.help.source.exploreKinds
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 容器编辑对话框(管理页点行 / 发现页长按菜单共用)。
 * 可换书源/换分类:换书源后必须重选分类,改动在确定时一并落库
 */
class ExploreContainerEditDialog : BaseDialogFragment(R.layout.dialog_explore_container_edit, true) {

    companion object {
        private const val PICK_SOURCE_KEY = "exploreEditPickSource"

        fun edit(id: Long) = ExploreContainerEditDialog().apply {
            arguments = Bundle().apply { putLong("id", id) }
        }
    }

    private val binding by viewBinding(DialogExploreContainerEditBinding::bind)
    private var container: ExploreContainer? = null

    /** 进入编辑时的指向快照,保存时据此判断是否清旧缓存 */
    private var originTarget: Triple<String, String, String>? = null
    private var pickingKinds = false

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.tvCancel.setOnClickListener { dismiss() }
        binding.tvOk.setOnClickListener { save() }
        binding.tvDelete.setOnClickListener { delete() }
        binding.tvChangeSource.setOnClickListener {
            showDialogFragment(ExploreSourcePickerDialog.pick(PICK_SOURCE_KEY))
        }
        binding.tvChangeKind.setOnClickListener { pickKind() }
        childFragmentManager.setFragmentResultListener(
            PICK_SOURCE_KEY, viewLifecycleOwner
        ) { _, bundle ->
            bundle.getString("sourceUrl")?.let { onSourcePicked(it) }
        }
        val id = arguments?.getLong("id", -1) ?: -1
        viewLifecycleOwner.lifecycleScope.launch {
            val c = withContext(IO) { appDb.exploreContainerDao.getById(id) }
            if (c == null) {
                toastOnUi(R.string.explore_source_not_found)
                dismiss()
                return@launch
            }
            container = c
            originTarget = Triple(c.sourceUrl, c.kindUrl, c.kindTitle)
            upView(c)
        }
    }

    private fun upView(c: ExploreContainer) = binding.run {
        upSourceInfo(c)
        etTitle.setText(c.customTitle)
        if (c.style == ExploreContainer.STYLE_LIST) {
            rbList.isChecked = true
        } else {
            rbFlow.isChecked = true
        }
        etCount.setText(c.listCount.toString())
    }

    private fun upSourceInfo(c: ExploreContainer) {
        binding.tvSourceInfo.text = "${c.sourceName} · ${c.kindTitle}"
    }

    private fun onSourcePicked(sourceUrl: String) {
        val c = container ?: return
        selectKind(sourceUrl) { source, kind ->
            c.sourceUrl = source.bookSourceUrl
            c.sourceName = source.bookSourceName
            c.kindTitle = kind.title
            c.kindUrl = kind.url!!
            upSourceInfo(c)
        }
    }

    private fun pickKind() {
        val c = container ?: return
        selectKind(c.sourceUrl) { _, kind ->
            c.kindTitle = kind.title
            c.kindUrl = kind.url!!
            upSourceInfo(c)
        }
    }

    /** 加载书源分类后弹单选列表 */
    private fun selectKind(sourceUrl: String, onSelect: (BookSource, ExploreKind) -> Unit) {
        if (pickingKinds) return
        pickingKinds = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val source = withContext(IO) { appDb.bookSourceDao.getBookSource(sourceUrl) }
                if (source == null) {
                    toastOnUi(R.string.explore_source_not_found)
                    return@launch
                }
                val kinds = withContext(IO) {
                    source.exploreKinds().filter {
                        !it.url.isNullOrBlank() && !it.title.startsWith("ERROR:")
                    }
                }
                if (kinds.isEmpty()) {
                    toastOnUi(R.string.explore_no_kinds)
                    return@launch
                }
                requireContext().selector(
                    "${source.bookSourceName} · ${getString(R.string.explore_select_kind)}",
                    kinds.map { it.title }
                ) { _, i ->
                    onSelect(source, kinds[i])
                }
            } finally {
                pickingKinds = false
            }
        }
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
        val targetChanged = originTarget != Triple(c.sourceUrl, c.kindUrl, c.kindTitle)
        viewLifecycleOwner.lifecycleScope.launch {
            withContext(IO) {
                appDb.exploreContainerDao.update(c)
                // 指向变了:旧分类的缓存作废,防止下次冷启动水合出旧书
                if (targetChanged) ExploreContainerHelp.removeCache(c.id)
            }
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
