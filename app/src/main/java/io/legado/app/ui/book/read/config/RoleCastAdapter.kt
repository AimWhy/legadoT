package io.legado.app.ui.book.read.config

import android.content.Context
import android.view.View
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
        fun onMerge(cast: RoleCast)
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
        ivMerge.visibility = if (item.roleName == RoleCast.NARRATOR) View.GONE else View.VISIBLE
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemRoleCastBinding) {
        binding.tvRoleVoice.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { callBack.onPickVoice(it) }
        }
        binding.ivPreview.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { callBack.onPreview(it) }
        }
        binding.ivMerge.setOnClickListener {
            getItemByLayoutPosition(holder.layoutPosition)?.let { callBack.onMerge(it) }
        }
    }

    private fun voiceLabelOf(cast: RoleCast): String {
        val match = voices.firstOrNull {
            it.engineId == cast.ttsEngineId && it.voice?.id == cast.voice
        }
        if (match != null) {
            return "${match.engineName} · ${match.voice?.name ?: context.getString(io.legado.app.R.string.role_cast_default_voice)}"
        }
        if (cast.ttsEngineId > 0L) {
            return context.getString(
                io.legado.app.R.string.role_cast_unavailable,
                cast.voice ?: cast.ttsEngineId.toString()
            )
        }
        return cast.voice ?: context.getString(io.legado.app.R.string.role_cast_auto)
    }

    private fun buildDesc(cast: RoleCast): String {
        val gender = cast.gender?.takeIf { it != TtsVoice.GENDER_UNKNOWN }.orEmpty()
        val seen = context.getString(io.legado.app.R.string.role_cast_seen, cast.lastSeenChapter + 1)
        return listOf(gender, seen).filter { it.isNotBlank() }.joinToString(" · ")
    }
}
