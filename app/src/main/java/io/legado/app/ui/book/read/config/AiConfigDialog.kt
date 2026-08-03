package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogAiConfigBinding
import io.legado.app.help.ai.AiClient
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

class AiConfigDialog : BaseDialogFragment(R.layout.dialog_ai_config, true) {

    private val binding by viewBinding(DialogAiConfigBinding::bind)

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        etAiBaseUrl.setText(AppConfig.aiBaseUrl)
        etAiApiKey.setText(AppConfig.aiApiKey)
        etAiModel.setText(AppConfig.aiModel)
        etAiPrompt.setText(AppConfig.aiRolePrompt)
        btnTest.setOnClickListener {
            save()
            btnTest.isEnabled = false
            lifecycleScope.launch {
                val result = AiClient.testConnection()
                btnTest.isEnabled = true
                result.onSuccess {
                    toastOnUi(R.string.ai_test_ok)
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: it.javaClass.simpleName)
                }
            }
        }
    }

    override fun onDestroyView() {
        save()
        super.onDestroyView()
    }

    private fun save() = binding.run {
        AppConfig.aiBaseUrl = etAiBaseUrl.text.toString().trim()
        AppConfig.aiApiKey = etAiApiKey.text.toString().trim()
        AppConfig.aiModel = etAiModel.text.toString().trim()
        AppConfig.aiRolePrompt = etAiPrompt.text.toString()
    }
}
