package io.legado.app.help.update

import android.os.Build
import androidx.annotation.Keep
import io.legado.app.constant.AppConst
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.http.text
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl

@Keep
@Suppress("unused")
object AppUpdateGitHub : AppUpdate.AppUpdateInterface {

    private val manifestUrls = listOf(
        "https://skybook.qzz.io/legado-download/update.json"
    )

    private val checkVariant: AppVariant
        get() = when (AppConfig.updateToVariant) {
            "official_version" -> AppVariant.OFFICIAL
            "beta_release_version" -> AppVariant.BETA_RELEASE
            "beta_releaseA_version" -> AppVariant.BETA_RELEASEA
            else -> AppConst.appInfo.appVariant.takeUnless { it == AppVariant.UNKNOWN } ?: AppVariant.OFFICIAL
        }

    private val checkChannel: String
        get() = if (checkVariant.isBeta()) "beta" else "release"

    private suspend fun getManifestUpdate(): UpdateManifestResult {
        var lastError: Throwable? = null
        manifestUrls.forEach { manifestUrl ->
            try {
                val url = manifestUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("channel", checkChannel)
                    .build()
                val res = okHttpClient.newCallResponse {
                    url(url)
                }
                if (!res.isSuccessful) {
                    lastError = NoStackTraceException("获取更新清单出错(${res.code})")
                    return@forEach
                }
                val body = res.body.text()
                if (body.isBlank()) {
                    lastError = NoStackTraceException("获取更新清单出错")
                    return@forEach
                }
                val manifest = GSON.fromJsonObject<UpdateManifest>(body)
                    .getOrElse {
                        lastError = it
                        return@forEach
                    }
                return UpdateManifestSelector.toUpdateResult(
                    manifest = manifest,
                    currentVersionName = AppConst.appInfo.versionName,
                    currentVersionCode = AppConst.appInfo.versionCode,
                    supportedAbis = Build.SUPPORTED_ABIS.toList()
                )
            } catch (e: Throwable) {
                lastError = e
            }
        }
        return UpdateManifestResult.Unavailable(
            lastError?.localizedMessage ?: "获取更新清单出错"
        )
    }

    override fun check(
        scope: CoroutineScope,
    ): Coroutine<AppUpdate.UpdateInfo> {
        return Coroutine.async(scope) {
            when (val result = getManifestUpdate()) {
                is UpdateManifestResult.HasUpdate -> result.updateInfo
                UpdateManifestResult.NoUpdate -> throw NoStackTraceException("已是最新版本")
                is UpdateManifestResult.Unavailable -> throw NoStackTraceException(result.message)
            }
        }.timeout(10000)
    }
}
