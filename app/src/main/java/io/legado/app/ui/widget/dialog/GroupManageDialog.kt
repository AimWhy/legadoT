package io.legado.app.ui.widget.dialog

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemGroupManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.book.source.manage.BookSourceViewModel
import io.legado.app.ui.main.explore.manage.ExploreManageViewModel
import io.legado.app.ui.replace.ReplaceRuleViewModel
import io.legado.app.ui.rss.source.manage.RssSourceViewModel
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.requestInputMethod
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * 字符串分组管理弹窗（书源/替换规则/RSS 源共用,R1 由三份近似拷贝合一）。
 * 分组操作经 [GroupOps] 接口分发到宿主 Activity 的对应 ViewModel。
 */
class GroupManageDialog() : BaseDialogFragment(R.layout.dialog_recycler_view),
    Toolbar.OnMenuItemClickListener {

    constructor(type: Type) : this() {
        arguments = Bundle().apply {
            putString("type", type.name)
        }
    }

    enum class Type { BookSource, ReplaceRule, RssSource, ExploreContainer }

    /** 三个宿主 ViewModel 的分组操作公共面（签名本就一致,声明实现即可） */
    interface GroupOps {
        fun addGroup(group: String)
        fun upGroup(oldGroup: String, newGroup: String?)
        fun delGroup(group: String)
    }

    private val type: Type
        get() = Type.valueOf(arguments?.getString("type") ?: Type.BookSource.name)

    private val groupOps: GroupOps by lazy {
        val provider = ViewModelProvider(requireActivity())
        when (type) {
            Type.BookSource -> provider[BookSourceViewModel::class.java]
            Type.ReplaceRule -> provider[ReplaceRuleViewModel::class.java]
            Type.RssSource -> provider[RssSourceViewModel::class.java]
            Type.ExploreContainer -> provider[ExploreManageViewModel::class.java]
        }
    }

    private val groupsFlow: Flow<List<String>>
        get() = when (type) {
            Type.BookSource -> appDb.bookSourceDao.flowGroups()
            Type.ReplaceRule -> appDb.replaceRuleDao.flowGroups()
            Type.RssSource -> appDb.rssSourceDao.flowGroups()
            Type.ExploreContainer -> appDb.exploreContainerDao.flowGroups()
        }

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.title = getString(R.string.group_manage)
        binding.toolBar.inflateMenu(R.menu.group_manage)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.addItemDecoration(VerticalDivider(requireContext()))
        binding.recyclerView.adapter = adapter
        binding.tvOk.visible()
        binding.tvOk.setOnClickListener {
            dismissAllowingStateLoss()
        }
        initData()
    }

    private fun initData() {
        lifecycleScope.launch {
            groupsFlow.conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> addGroup()
        }
        return true
    }

    @SuppressLint("InflateParams")
    private fun addGroup() {
        alert(title = getString(R.string.add_group)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotBlank()) {
                        groupOps.addGroup(it)
                    }
                }
            }
            cancelButton()
        }.requestInputMethod()
    }

    @SuppressLint("InflateParams")
    private fun editGroup(group: String) {
        alert(title = getString(R.string.group_edit)) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setText(group)
            }
            customView { alertBinding.root }
            okButton {
                groupOps.upGroup(group, alertBinding.editView.text?.toString())
            }
            cancelButton()
        }.requestInputMethod()
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<String, ItemGroupManageBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemGroupManageBinding {
            return ItemGroupManageBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupManageBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.run {
                tvGroup.text = item
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemGroupManageBinding) {
            binding.apply {
                tvEdit.setOnClickListener {
                    getItem(holder.layoutPosition)?.let {
                        editGroup(it)
                    }
                }
                tvDel.setOnClickListener {
                    getItem(holder.layoutPosition)?.let { groupOps.delGroup(it) }
                }
            }
        }
    }

}
