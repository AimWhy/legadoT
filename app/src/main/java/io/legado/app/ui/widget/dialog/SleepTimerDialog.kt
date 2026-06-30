package io.legado.app.ui.widget.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogSleepTimerBinding
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.gone
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

/**
 * 听书/音频书「停止设置」共用底部弹窗:
 * 上段定时(分钟)、下段按集数,各含 2 个常用预设 + 1 个「上次设置」+ 自定义输入。
 * 宿主实现 [CallBack] 路由到对应播放器(TTS / 音频)。
 */
class SleepTimerDialog : BottomSheetDialogFragment() {

    interface CallBack {
        /** 定时(分钟, 0=关闭) */
        fun onSleepTimerMinute(minute: Int)

        /** 按集数(章数, 0=关闭) */
        fun onSleepTimerChapter(count: Int)
    }

    private var _binding: DialogSleepTimerBinding? = null
    private val binding get() = _binding!!
    private val callBack get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogSleepTimerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.run {
            tvTimeP1.text = getString(R.string.timer_m, TIME_PRESETS[0])
            tvTimeP2.text = getString(R.string.timer_m, TIME_PRESETS[1])
            tvChapterP1.text = getString(R.string.read_aloud_stop_chapters, CHAPTER_PRESETS[0])
            tvChapterP2.text = getString(R.string.read_aloud_stop_chapters, CHAPTER_PRESETS[1])

            tvTimeP1.setOnClickListener { applyMinute(TIME_PRESETS[0], save = false) }
            tvTimeP2.setOnClickListener { applyMinute(TIME_PRESETS[1], save = false) }
            tvChapterP1.setOnClickListener { applyChapter(CHAPTER_PRESETS[0], save = false) }
            tvChapterP2.setOnClickListener { applyChapter(CHAPTER_PRESETS[1], save = false) }

            tvTimeOk.setOnClickListener {
                val m = etTime.text.toString().toIntOrNull()
                if (m == null || m <= 0) {
                    requireContext().toastOnUi(R.string.sleep_timer_minute_hint)
                } else {
                    applyMinute(m, save = true)
                }
            }
            tvChapterOk.setOnClickListener {
                val c = etChapter.text.toString().toIntOrNull()
                if (c == null || c <= 0) {
                    requireContext().toastOnUi(R.string.sleep_timer_chapter_hint)
                } else {
                    applyChapter(c, save = true)
                }
            }

            tvClear.setOnClickListener { applyMinute(0, save = false) }

            val lastMin = requireContext().getPrefInt(PreferKey.lastSleepTimer, 0)
            if (lastMin > 0) {
                tvTimeLast.visible()
                tvTimeLast.text =
                    getString(R.string.sleep_timer_last, getString(R.string.timer_m, lastMin))
                tvTimeLast.setOnClickListener { applyMinute(lastMin, save = false) }
            } else {
                tvTimeLast.gone()
            }
            val lastChapter = requireContext().getPrefInt(PreferKey.lastSleepChapter, 0)
            if (lastChapter > 0) {
                tvChapterLast.visible()
                tvChapterLast.text = getString(
                    R.string.sleep_timer_last,
                    getString(R.string.read_aloud_stop_chapters, lastChapter)
                )
                tvChapterLast.setOnClickListener { applyChapter(lastChapter, save = false) }
            } else {
                tvChapterLast.gone()
            }
        }
    }

    private fun applyMinute(minute: Int, save: Boolean) {
        if (save && minute > 0) requireContext().putPrefInt(PreferKey.lastSleepTimer, minute)
        callBack?.onSleepTimerMinute(minute)
        dismiss()
    }

    private fun applyChapter(count: Int, save: Boolean) {
        if (save && count > 0) requireContext().putPrefInt(PreferKey.lastSleepChapter, count)
        callBack?.onSleepTimerChapter(count)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val TIME_PRESETS = intArrayOf(30, 60)
        private val CHAPTER_PRESETS = intArrayOf(1, 3)
    }
}
