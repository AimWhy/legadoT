package io.legado.app.ui.rss.article

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityRssArtivlesBinding
import io.legado.app.help.source.sortUrls
import io.legado.app.lib.theme.accentColor
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.*
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RssSortActivity : VMBaseActivity<ActivityRssArtivlesBinding, RssSortViewModel>(),
    VariableDialog.Callback {

    override val binding by viewBinding(ActivityRssArtivlesBinding::inflate)
    override val viewModel by viewModels<RssSortViewModel>()
    private val adapter by lazy { TabFragmentPageAdapter(this) }
    private val sortList = mutableListOf<Pair<String, String>>()
    private var fragmentGeneration = 0L
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.initData(intent) {
                upFragments()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.viewPager.adapter = adapter
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = sortList[position].first
        }.attach()
        binding.tabLayout.setSelectedTabIndicatorColor(accentColor)
        viewModel.titleLiveData.observe(this) {
            binding.titleBar.title = it
        }
        viewModel.initData(intent) {
            upFragments()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.rss_articles, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.rssSource?.loginUrl.isNullOrBlank()
        return super.onMenuOpened(featureId, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }

            R.id.menu_refresh_sort -> viewModel.clearSortCache { upFragments() }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_edit_source -> viewModel.rssSource?.sourceUrl?.let {
                editSourceResult.launch {
                    putExtra("sourceUrl", it)
                }
            }

            R.id.menu_clear -> {
                viewModel.url?.let {
                    viewModel.clearArticles()
                }
            }

            R.id.menu_switch_layout -> {
                viewModel.switchLayout()
                upFragments()
            }

            R.id.menu_read_record -> {
                showDialogFragment<ReadRecordDialog>()
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun upFragments() {
        lifecycleScope.launch {
            viewModel.rssSource?.sortUrls()?.let {
                sortList.clear()
                sortList.addAll(it)
            }
            if (sortList.size == 1) {
                binding.tabLayout.gone()
            } else {
                binding.tabLayout.visible()
            }
            // 原 getItemPosition 恒 POSITION_NONE:每次刷新全量重建(内容/布局风格可能变);
            // 递增 generation 使所有 itemId 变化,等价全量重建
            fragmentGeneration++
            adapter.notifyDataSetChanged()
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.rssSource
            if (source == null) {
                toastOnUi("源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(Dispatchers.IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    private inner class TabFragmentPageAdapter(activity: FragmentActivity) :
        FragmentStateAdapter(activity) {

        override fun getItemCount(): Int {
            return sortList.size
        }

        override fun getItemId(position: Int): Long {
            // 高位=generation,低位=position:刷新后 generation 变→所有 id 变→全量重建,等价原 POSITION_NONE
            return (fragmentGeneration shl 20) or position.toLong()
        }

        override fun containsItem(itemId: Long): Boolean {
            return (itemId ushr 20) == fragmentGeneration && (itemId and 0xFFFFF) < sortList.size
        }

        override fun createFragment(position: Int): Fragment {
            val sort = sortList[position]
            return RssArticlesFragment(sort.first, sort.second)
        }
    }

}