package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.data.appDb
import io.legado.app.data.entities.RoleCast
import io.legado.app.databinding.DialogRoleCastBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.model.ReadBook
import io.legado.app.model.readaloud.RoleCastManager
import io.legado.app.model.readaloud.VoiceRef
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoleCastDialog : BaseDialogFragment(R.layout.dialog_role_cast),
    RoleCastAdapter.CallBack {

    private val binding by viewBinding(DialogRoleCastBinding::bind)
    private lateinit var adapter: RoleCastAdapter
    private val bookUrl get() = ReadBook.book?.bookUrl.orEmpty()
    private var voices: List<VoiceRef> = emptyList()

    override fun onStart() {
        super.onStart()
        setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        adapter = RoleCastAdapter(requireContext(), voices, this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.btnReset.setOnClickListener { confirmReset() }
        refresh()
    }

    private fun confirmReset() {
        alert(R.string.role_cast_reset) {
            setMessage(R.string.sure)
            positiveButton(R.string.yes) {
                lifecycleScope.launch {
                    withContext(IO) { appDb.roleCastDao.deleteByBook(bookUrl) }
                    refresh()
                }
            }
            negativeButton(R.string.no)
        }
    }

    private fun refresh() {
        lifecycleScope.launch {
            val casts = withContext(IO) {
                appDb.roleCastDao.getByBook(bookUrl).sortedBy { it.roleName }
            }
            val pool = withContext(IO) {
                RoleCastManager.availableVoices()
            }
            voices = pool
            adapter.updateVoices(pool)
            adapter.setItems(casts)
            binding.btnReset.isEnabled = casts.isNotEmpty()
        }
    }

    override fun onPickVoice(cast: RoleCast) {
        if (voices.isEmpty()) {
            toastOnUi(R.string.role_cast_empty)
            return
        }
        val labels = voices.map { "${it.voice.name}（${it.voice.gender}）" }
        requireContext().selector(getString(R.string.role_cast), labels) { _, index ->
            val picked = voices[index]
            lifecycleScope.launch {
                withContext(IO) {
                    appDb.roleCastDao.insert(
                        cast.copy(
                            ttsEngineId = picked.engineId,
                            voice = picked.voice.id,
                            isManual = true
                        )
                    )
                }
                refresh()
            }
        }
    }

    override fun onPreview(cast: RoleCast) {
        toastOnUi(R.string.role_preview_toast)
    }
}
