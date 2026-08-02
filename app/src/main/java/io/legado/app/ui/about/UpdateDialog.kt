package io.legado.app.ui.about

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogUpdateBinding
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.update.AppUpdate
import io.legado.app.model.Download
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.openUrl
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class UpdateDialog() : BaseDialogFragment(R.layout.dialog_update) {

    constructor(updateInfo: AppUpdate.UpdateInfo) : this() {
        arguments = Bundle().apply {
            putString("newVersion", updateInfo.tagName)
            putString("updateBody", updateInfo.updateLog)
            putString("url", updateInfo.downloadUrl)
            putString("name", updateInfo.fileName)
            putLong("size", updateInfo.size)
            putString("publishedAt", updateInfo.publishedAt)
            putString("pageUrl", updateInfo.pageUrl)
            putString("backupUrl", updateInfo.backupDownloadUrl)
        }
    }

    val binding by viewBinding(DialogUpdateBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.title = arguments?.getString("newVersion")
        upSubtitle()
        val updateBody = arguments?.getString("updateBody")
        if (updateBody == null) {
            toastOnUi("没有数据")
            dismiss()
            return
        }
        binding.textView.post {
            Markwon.builder(requireContext())
                .usePlugin(GlideImagesPlugin.create(requireContext()))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(TablePlugin.create(requireContext()))
                .build()
                .setMarkdown(binding.textView, updateBody)
        }
        binding.toolBar.inflateMenu(R.menu.app_update)
        binding.toolBar.menu.findItem(R.id.menu_download_backup)?.isVisible =
            !arguments?.getString("backupUrl").isNullOrBlank()
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_download -> startDownload(arguments?.getString("url"))
                R.id.menu_download_backup -> startDownload(arguments?.getString("backupUrl"))
                R.id.menu_open_in_browser -> openInBrowser()
                R.id.menu_ignore_version -> ignoreVersion()
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun upSubtitle() {
        val parts = mutableListOf<String>()
        val size = arguments?.getLong("size") ?: 0L
        if (size > 0) {
            parts.add(ConvertUtils.formatFileSize(size))
        }
        formatPublishDate(arguments?.getString("publishedAt"))?.let {
            parts.add(it)
        }
        binding.toolBar.subtitle = parts.joinToString(" · ")
    }

    private fun formatPublishDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        return runCatching {
            DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .withZone(ZoneId.systemDefault())
                .format(Instant.parse(iso))
        }.getOrElse {
            iso.substringBefore('T').takeIf { date -> date.isNotBlank() }
        }
    }

    private fun startDownload(url: String?) {
        val name = arguments?.getString("name")
        if (url.isNullOrBlank() || name.isNullOrBlank()) return
        Download.start(requireContext(), url, name)
        toastOnUi(R.string.download_start)
    }

    private fun openInBrowser() {
        val target = sequenceOf("pageUrl", "backupUrl", "url")
            .mapNotNull { arguments?.getString(it) }
            .firstOrNull { it.isNotBlank() } ?: return
        requireContext().openUrl(target)
    }

    private fun ignoreVersion() {
        LocalConfig.ignoreUpdateVersion = arguments?.getString("newVersion")
        toastOnUi("已忽略该版本,自动检查更新时不再提醒")
        dismiss()
    }

}
