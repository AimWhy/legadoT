package io.legado.app.ui.main.explore.manage

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.databinding.ActivityExploreManageBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.setupManagePage
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发现容器管理
 */
class ExploreManageActivity :
    VMBaseActivity<ActivityExploreManageBinding, ExploreManageViewModel>(),
    PopupMenu.OnMenuItemClickListener,
    SelectActionBar.CallBack,
    ExploreManageAdapter.CallBack {

    override val binding by viewBinding(ActivityExploreManageBinding::inflate)
    override val viewModel by viewModels<ExploreManageViewModel>()
    private val adapter by lazy { ExploreManageAdapter(this, this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        observeData()
        initSelectActionBar()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explore_manage, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_container -> showDialogFragment(ExploreSourcePickerDialog())
            R.id.menu_enable_all -> viewModel.enableAll(true)
            R.id.menu_disable_all -> viewModel.enableAll(false)
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.setupManagePage(
            adapter,
            ItemTouchCallback(adapter).apply { isCanDrag = true },
            adapter.dragSelectCallback,
        )
    }

    private fun observeData() {
        lifecycleScope.launch {
            appDb.exploreContainerDao.flowAll()
                .catch { AppLog.put("发现容器管理界面更新数据出错", it) }
                .flowOn(IO).conflate().collect {
                    adapter.setItems(it, adapter.diffItemCallBack)
                }
        }
    }

    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.explore_manage_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) adapter.selectAll() else adapter.revertSelection()
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        val selection = adapter.selection
        if (selection.isEmpty()) return
        alert(R.string.draw) {
            setMessage(getString(R.string.explore_del_selection, selection.size))
            noButton()
            yesButton { viewModel.deleteSelection(selection) }
        }
    }

    override fun upCountView() {
        binding.selectActionBar.upCountView(adapter.selection.size, adapter.itemCount)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection, true)
            R.id.menu_disable_selection -> viewModel.enableSelection(adapter.selection, false)
            R.id.menu_set_group_sel -> selectionSetGroup()
        }
        return true
    }

    /** 设分组:取消分组(置空)| 已有分组 | 新建分组…;选中集在弹框前快照 */
    private fun selectionSetGroup() {
        val selection = adapter.selection
        if (selection.isEmpty()) return
        lifecycleScope.launch {
            val groups = withContext(IO) {
                appDb.exploreContainerDao.all
                    .map { it.groupName }.filter { it.isNotEmpty() }.distinct()
            }
            val options = buildList {
                add(getString(R.string.explore_clear_group))
                addAll(groups)
                add(getString(R.string.explore_new_group))
            }
            selector(getString(R.string.explore_set_group), options) { _, i ->
                when (i) {
                    0 -> viewModel.setGroupSelection(selection, "")
                    options.lastIndex -> inputNewGroup(selection)
                    else -> viewModel.setGroupSelection(selection, groups[i - 1])
                }
            }
        }
    }

    /** 新建分组输入;trim 后为空 = 放弃本次操作(不写库) */
    private fun inputNewGroup(selection: List<ExploreContainer>) {
        val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
            editView.hint = getString(R.string.explore_group_name)
        }
        alert(R.string.explore_new_group) {
            customView { alertBinding.root }
            yesButton {
                val name = alertBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    viewModel.setGroupSelection(selection, name)
                }
            }
            noButton()
        }
    }

    override fun update(vararg container: ExploreContainer) = viewModel.update(*container)

    override fun delete(container: ExploreContainer) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + container.getDisplayTitle())
            noButton()
            yesButton { viewModel.delete(container) }
        }
    }

    override fun edit(container: ExploreContainer) {
        showDialogFragment(ExploreContainerEditDialog.edit(container.id))
    }

    override fun toTop(container: ExploreContainer) = viewModel.toTop(container)
    override fun toBottom(container: ExploreContainer) = viewModel.toBottom(container)
    override fun upOrder() = viewModel.upOrder()
}
