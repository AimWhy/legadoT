package io.legado.app.ui.main.explore.manage

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.ExploreContainer
import io.legado.app.databinding.ActivityExploreManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 发现容器管理
 */
class ExploreManageActivity :
    VMBaseActivity<ActivityExploreManageBinding, ExploreManageViewModel>(),
    ExploreManageAdapter.CallBack {

    override val binding by viewBinding(ActivityExploreManageBinding::inflate)
    override val viewModel by viewModels<ExploreManageViewModel>()
    private val adapter by lazy { ExploreManageAdapter(this, this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        observeData()
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
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        val itemTouchCallback = ItemTouchCallback(adapter)
        itemTouchCallback.isCanDrag = true
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
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
