package io.legado.app.ui.book.toc

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

class TocActivityResult : ActivityResultContract<String, TocActivityResult.Result?>() {

    /** [anchorText] 标注跳转携带的划线原文, 供落位后重锚纠偏 */
    data class Result(
        val index: Int,
        val chapterPos: Int,
        val chapterChanged: Boolean,
        val anchorText: String? = null,
    )

    override fun createIntent(context: Context, input: String): Intent {
        return Intent(context, TocActivity::class.java)
            .putExtra("bookUrl", input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Result? {
        if (resultCode == RESULT_OK) {
            intent?.let {
                return Result(
                    it.getIntExtra("index", 0),
                    it.getIntExtra("chapterPos", 0),
                    it.getBooleanExtra("chapterChanged", false),
                    it.getStringExtra("anchorText"),
                )
            }
        }
        return null
    }
}
