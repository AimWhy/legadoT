package io.legado.app.lib.theme

import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import io.legado.app.R

/**
 * N4 主题 UX:中式意象预设主题。
 *
 * 数据形态=代码常量(不入库):每套仅是一枚"种子色 + 中式名",
 * 点选时交给 [ThemeSeedApplier.applySeed] 以 `mode = "preset:$id"` 派生四色落盘。
 * id 同时是选中态标识(与 [io.legado.app.help.config.ThemeConfig.themeSeedMode] 的
 * `preset:<id>` 后缀比对)。
 */
data class PresetTheme(
    val id: String,
    @param:StringRes val nameRes: Int,
    @param:ColorInt val seed: Int,
)

object PresetThemes {
    val all = listOf(
        PresetTheme("daiqing", R.string.preset_daiqing, 0xFF33628C.toInt()),  // 黛青·青蓝
        PresetTheme("zhuyue", R.string.preset_zhuyue, 0xFF2E7D6B.toInt()),    // 竹月·青绿
        PresetTheme("zhusha", R.string.preset_zhusha, 0xFFC03F3C.toInt()),    // 朱砂·赤红
        PresetTheme("ehuang", R.string.preset_ehuang, 0xFFC7962D.toInt()),    // 鹅黄·暖黄
        PresetTheme("songyan", R.string.preset_songyan, 0xFF3E5C43.toInt()),  // 松烟·墨绿
        PresetTheme("haitang", R.string.preset_haitang, 0xFFC25B77.toInt()),  // 海棠·粉红
        PresetTheme("qingci", R.string.preset_qingci, 0xFF4E8F87.toInt()),    // 青瓷·淡青
        PresetTheme("moshi", R.string.preset_moshi, 0xFF5A5F66.toInt()),      // 墨石·中性
        // 明快风格扩充:偏艳 4 色柔化(降饱和+微调明度,阅读友好)
        PresetTheme("zheju", R.string.preset_zheju, 0xFFC7643F.toInt()),       // 赭橙·暖橙(橘红柔化)
        PresetTheme("tanfen", R.string.preset_tanfen, 0xFFB85C7E.toInt()),     // 檀粉·雅粉(荔枝柔化)
        PresetTheme("qiuxiang", R.string.preset_qiuxiang, 0xFFC99A3C.toInt()), // 秋香·金黄(明黄柔化)
        PresetTheme("ouhe", R.string.preset_ouhe, 0xFF7A5C99.toInt()),         // 藕荷·雅紫(藤紫柔化)
        // 明快风格扩充:耐看 3 色原值保留
        PresetTheme("tianqing", R.string.preset_tianqing, 0xFF2196C4.toInt()), // 天青·湖蓝
        PresetTheme("dianlan", R.string.preset_dianlan, 0xFF3F51B5.toInt()),   // 靛蓝·靛青
        PresetTheme("bohe", R.string.preset_bohe, 0xFF26A96C.toInt()),         // 薄荷·明绿
    )
}
