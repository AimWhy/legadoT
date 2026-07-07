package io.legado.app.ui.qrcode

import android.os.Bundle
import android.view.View
import com.google.zxing.Result
import com.king.camera.scan.AnalyzeResult
import com.king.camera.scan.CameraScan
import com.king.view.viewfinderview.ViewfinderView
import com.king.zxing.BarcodeCameraScanFragment
import com.king.zxing.DecodeConfig
import com.king.zxing.DecodeFormatManager
import com.king.zxing.analyze.MultiFormatAnalyzer
import io.legado.app.lib.theme.accentColor

class QrCodeFragment : BarcodeCameraScanFragment() {

    override fun initCameraScan(cameraScan: CameraScan<Result>) {
        super.initCameraScan(cameraScan)
        //初始化解码配置
        val decodeConfig = DecodeConfig()
        //如果只有识别二维码的需求，这样设置效率会更高，不设置默认为DecodeFormatManager.DEFAULT_HINTS
        decodeConfig.hints = DecodeFormatManager.QR_CODE_HINTS
        //设置是否全区域识别，默认false
        decodeConfig.isFullAreaScan = true
        //设置识别区域比例，默认0.8，设置的比例最终会在预览区域裁剪基于此比例的一个矩形进行扫码识别
        decodeConfig.areaRectRatio = 0.8f

        //在启动预览之前，设置分析器，只识别二维码
        cameraScan.setAnalyzer(MultiFormatAnalyzer(decodeConfig))
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        //取景框施色跟随四色换肤：运行时 setter 优于 vvViewfinderStyle 静态主题挂钩
        view.findViewById<ViewfinderView>(com.king.zxing.R.id.viewfinderView)?.apply {
            val accent = requireContext().accentColor
            setFrameCornerColor(accent)
            setLaserColor(accent)
            setFrameColor(accent)
            setFrameCornerRadius(resources.getDimensionPixelSize(io.legado.app.R.dimen.radius_s))
        }
    }

    override fun onScanResultCallback(result: AnalyzeResult<Result>) {
        cameraScan.setAnalyzeImage(false)
        (activity as? QrCodeActivity)?.onScanResultCallback(result.result)
    }

}