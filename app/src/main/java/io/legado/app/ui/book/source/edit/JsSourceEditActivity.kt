package io.legado.app.ui.book.source.edit

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.databinding.ActivityJsSourceEditBinding
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.jsSource.JsSourceConfig
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.utils.getClipText
import io.legado.app.utils.imeHeight
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding

/**
 * 纯JS单文件源编辑器(spec §5):整页 CodeView,脚本是元数据唯一真理源,
 * 保存=JsSourceConfig.extract 重提 config 覆盖元字段(enabled/customOrder 等用户态保留)。
 */
class JsSourceEditActivity : BaseActivity<ActivityJsSourceEditBinding>() {

    override val binding by viewBinding(ActivityJsSourceEditBinding::inflate)

    // 打开时的 bookSourceUrl,用于保存时识别脚本内改名(反查旧记录用,而非保存后的新 URL)
    private var openedSourceUrl: String? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.codeView.addJsPattern()
        // 本页无 KeyboardToolPop 工具条兜底,与书源编辑器(imeHeight==0 时才用 navigationBarHeight)不同,
        // 这里 IME 高度直接作为 padding,让输入区始终不被键盘遮挡
        binding.codeView.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            val imeHeight = windowInsets.imeHeight
            view.bottomPadding = if (imeHeight == 0) windowInsets.navigationBarHeight else imeHeight
            windowInsets
        }
        val sourceUrl = intent.getStringExtra("sourceUrl")
        if (sourceUrl.isNullOrBlank()) {
            assets.open("js_source_template.js").use {
                binding.codeView.setText(String(it.readBytes()))
            }
        } else {
            openedSourceUrl = sourceUrl
            lifecycleScope.launch {
                val source = withContext(IO) { appDb.bookSourceDao.getBookSource(sourceUrl) }
                binding.codeView.setText(source?.mainJs.orEmpty())
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.js_source_edit, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_save -> saveSource()
            R.id.menu_debug_source -> saveSource { source ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", source.bookSourceUrl)
                }
            }
            R.id.menu_copy_source -> sendToClip(binding.codeView.text.toString())
            R.id.menu_paste_source -> getClipText()?.let { binding.codeView.setText(it) }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun saveSource(onSuccess: ((BookSource) -> Unit)? = null) {
        val text = binding.codeView.text.toString()
        lifecycleScope.launch {
            runCatching {
                withContext(IO) {
                    val source = JsSourceConfig.extract(text, coroutineContext)
                    val openedUrl = openedSourceUrl
                    // 用户态字段来源:改名场景(openedSourceUrl 非空且与新 URL 不同)旧记录只能按
                    // openedSourceUrl 查到;直接导入后首次编辑等场景没有改名,退回按新 URL 查
                    val old = openedUrl?.let { appDb.bookSourceDao.getBookSource(it) }
                        ?: appDb.bookSourceDao.getBookSource(source.bookSourceUrl)
                    // 用户态字段不归脚本管:从旧记录保留(spec §5"脚本是元数据唯一真理源"的边界)
                    old?.let {
                        source.enabled = it.enabled
                        source.customOrder = it.customOrder
                        source.weight = it.weight
                        source.respondTime = it.respondTime
                        if (!it.equal(source)) {
                            source.lastUpdateTime = System.currentTimeMillis()
                        } else {
                            source.lastUpdateTime = it.lastUpdateTime
                        }
                    } ?: run {
                        source.lastUpdateTime = System.currentTimeMillis()
                    }
                    // 脚本内把 bookSourceUrl 改了名:旧行不会被新 URL 的 insert 覆盖,须显式删除,
                    // 否则旧记录成僵尸(仍 enabled 参与搜索),对齐 BookSourceEditViewModel.save() 的先例
                    if (openedUrl != null && openedUrl != source.bookSourceUrl) {
                        SourceHelp.deleteBookSource(openedUrl)
                    }
                    appDb.bookSourceDao.insert(source)
                    openedSourceUrl = source.bookSourceUrl
                    source
                }
            }.onSuccess {
                toastOnUi(R.string.success)
                onSuccess?.invoke(it)
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }
}
