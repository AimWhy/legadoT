package io.legado.app.ui.book.read.config

import android.content.Context
import android.view.ViewGroup
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.entities.RoleCast
import io.legado.app.data.entities.TtsVoice
import io.legado.app.databinding.ItemRoleCastBinding
import io.legado.app.model.readaloud.VoiceRef

class RoleCastAdapter(
    context: Context,
    private var voices: List<VoiceRef>,
    private val callBack: CallBack
) : RecyclerAdapter<RoleCast, ItemRoleCastBinding>(context) {

    interface CallBack {
        fun onPickVoice(cast: RoleCast)
        fun onPreview(cast: RoleCast)
    }

    fun updateVoices(newVoices: List<VoiceRef>) {
        voices = newVoices
    }

    override fun getViewBinding(parent: ViewGroup): ItemRoleCastBinding =
        ItemRoleCastBinding.inflate(inflater, parent, false)

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemRoleCastBinding,
        item: RoleCast,
        payloads: MutableList<Any>
    ) = binding.run {
        tvRoleName.text = item.roleName
        tvRoleDesc.text = buildDesc(item)
        tvRoleVoice.text = voiceLabelOf(item)
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemRoleCastBinding) {
        binding.tvRoleVoice.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { callBack.onPickVoice(it) }
        }
        binding.ivPreview.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { callBack.onPreview(it) }
        }
    }

    private fun voiceLabelOf(cast: RoleCast): String {
        val voiceId = cast.voice ?: return context.getString(io.legado.app.R.string.role_cast_auto)
        return voices.firstOrNull { it.engineId == cast.ttsEngineId && it.voice.id == voiceId }
            ?.voice?.name ?: voiceId
    }

    private fun buildDesc(cast: RoleCast): String {
        val gender = cast.gender?.takeIf { it != TtsVoice.GENDER_UNKNOWN }.orEmpty()
        val seen = context.getString(io.legado.app.R.string.role_cast_seen, cast.lastSeenChapter + 1)
        return listOf(gender, seen).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
