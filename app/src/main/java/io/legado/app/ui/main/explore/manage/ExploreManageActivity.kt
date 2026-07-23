package io.legado.app.ui.main.explore.manage

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
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
import io.legado.app.help.source.ExploreContainerHelp
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.dialog.GroupManageDialog
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.setupManagePage
import io.legado.app.utils.dpToPx
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
    private var allContainers: List<ExploreContainer> = emptyList()

    /** 分组筛选值:空="全部";no_group="无分组";group:前缀=分组名;命名空间见 ExploreContainerHelp */
    private var filterGroup: String = ""

    companion object {
        private const val KEY_FILTER_GROUP = "filterGroup"
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        filterGroup = savedInstanceState?.getString(KEY_FILTER_GROUP) ?: ""
        upFilterSubtitle()
        initRecyclerView()
        observeData()
        initSelectActionBar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_FILTER_GROUP, filterGroup)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.explore_manage, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_container -> showDialogFragment(ExploreSourcePickerDialog())
            R.id.menu_explore_group_filter -> showGroupFilterPopup()
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
                    allContainers = it
                    upFilterValidity()
                    upDisplayList()
                }
        }
    }

    private fun upDisplayList() {
        adapter.setItems(
            ExploreContainerHelp.filterByGroup(allContainers, filterGroup),
            adapter.diffItemCallBack
        )
    }

    /** 筛选中的分组被删/改名后回落"全部",对齐主页面 effectiveGroup 回退语义 */
    private fun upFilterValidity() {
        if (!filterGroup.startsWith(ExploreContainerHelp.GROUP_VALUE_PREFIX)) return
        val group = filterGroup.removePrefix(ExploreContainerHelp.GROUP_VALUE_PREFIX)
        if (allContainers.none { it.hasGroup(group) }) {
            filterGroup = ""
            upFilterSubtitle()
        }
    }

    private fun setFilterGroup(value: String) {
        filterGroup = value
        upFilterSubtitle()
        upDisplayList()
    }

    /** 页面无搜索框,筛选状态由副标题承载;"全部"清空副标题 */
    private fun upFilterSubtitle() {
        binding.titleBar.subtitle = when {
            filterGroup.isEmpty() -> ""
            filterGroup == ExploreContainerHelp.GROUP_VALUE_NO_GROUP ->
                getString(R.string.no_group)
            else -> filterGroup.removePrefix(ExploreContainerHelp.GROUP_VALUE_PREFIX)
        }
    }

    private fun showGroupFilterPopup() {
        val anchor = binding.titleBar.toolbar.findViewById<View>(R.id.menu_explore_group_filter)
            ?: binding.titleBar.toolbar
        lifecycleScope.launch {
            val groups = withContext(IO) {
                ExploreContainerHelp.dealGroups(
                    appDb.exploreContainerDao.all.map { it.groupName }
                )
            }
            PopupAction(this@ExploreManageActivity).apply {
                setVertical(true)
                setActionItems(buildList {
                    add(
                        PopupAction.PopupActionItem(
                            title = getString(R.string.group_manage),
                            value = ExploreContainerHelp.GROUP_VALUE_MANAGE
                        )
                    )
                    add(
                        PopupAction.PopupActionItem(
                            title = getString(R.string.all),
                            value = ExploreContainerHelp.GROUP_VALUE_ALL,
                            checked = filterGroup.isEmpty()
                        )
                    )
                    add(
                        PopupAction.PopupActionItem(
                            title = getString(R.string.no_group),
                            value = ExploreContainerHelp.GROUP_VALUE_NO_GROUP,
                            checked = filterGroup == ExploreContainerHelp.GROUP_VALUE_NO_GROUP
                        )
                    )
                    groups.forEach { g ->
                        val value = ExploreContainerHelp.GROUP_VALUE_PREFIX + g
                        add(
                            PopupAction.PopupActionItem(
                                title = g,
                                value = value,
                                checked = filterGroup == value
                            )
                        )
                    }
                })
                onActionClick = { value ->
                    dismiss()
                    when (value) {
                        ExploreContainerHelp.GROUP_VALUE_MANAGE -> showDialogFragment(
                            GroupManageDialog(GroupManageDialog.Type.ExploreContainer)
                        )
                        ExploreContainerHelp.GROUP_VALUE_ALL -> setFilterGroup("")
                        else -> setFilterGroup(value)
                    }
                }
                showAsDropDown(anchor, 0, 4.dpToPx())
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
            R.id.menu_add_group -> selectionAddToGroups()
            R.id.menu_remove_group -> selectionRemoveFromGroups()
        }
        return true
    }

    /** 批量加入分组;选中集在弹框前快照 */
    private fun selectionAddToGroups() {
        val selection = adapter.selection
        if (selection.isEmpty()) return
        lifecycleScope.launch {
            val groups = withContext(IO) {
                ExploreContainerHelp.dealGroups(
                    appDb.exploreContainerDao.all.map { it.groupName }
                )
            }
            alert(titleResource = R.string.add_group) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.setHint(R.string.group_name)
                    editView.setFilterValues(groups)
                    editView.dropDownHeight = 180.dpToPx()
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.editView.text?.toString()?.let {
                        if (it.isNotEmpty()) {
                            viewModel.selectionAddToGroups(selection, it)
                        }
                    }
                }
                cancelButton()
            }
        }
    }

    /** 批量移出分组;选中集在弹框前快照 */
    private fun selectionRemoveFromGroups() {
        val selection = adapter.selection
        if (selection.isEmpty()) return
        lifecycleScope.launch {
            val groups = withContext(IO) {
                ExploreContainerHelp.dealGroups(
                    appDb.exploreContainerDao.all.map { it.groupName }
                )
            }
            alert(titleResource = R.string.remove_group) {
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.setHint(R.string.group_name)
                    editView.setFilterValues(groups)
                    editView.dropDownHeight = 180.dpToPx()
                }
                customView { alertBinding.root }
                okButton {
                    alertBinding.editView.text?.toString()?.let {
                        if (it.isNotEmpty()) {
                            viewModel.selectionRemoveFromGroups(selection, it)
                        }
                    }
                }
                cancelButton()
            }
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
