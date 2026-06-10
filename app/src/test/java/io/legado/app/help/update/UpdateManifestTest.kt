package io.legado.app.help.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    @Test
    fun selectArtifact_prefersFirstSupportedAbi() {
        val artifacts = listOf(
            UpdateManifest.Artifact(
                abi = "armeabi-v7a",
                fileName = "legado_app_3.26.061010_armeabi-v7a.apk",
                url = "https://example.com/v7.apk"
            ),
            UpdateManifest.Artifact(
                abi = "arm64-v8a",
                fileName = "legado_app_3.26.061010_arm64-v8a.apk",
                url = "https://example.com/v8.apk"
            )
        )

        val selected = UpdateManifestSelector.selectArtifact(
            artifacts,
            supportedAbis = listOf("arm64-v8a", "armeabi-v7a")
        )

        assertEquals("legado_app_3.26.061010_arm64-v8a.apk", selected?.fileName)
    }

    @Test
    fun selectArtifact_usesUniversalWhenAbiDoesNotMatch() {
        val artifacts = listOf(
            UpdateManifest.Artifact(
                abi = "universal",
                fileName = "legado_app_3.26.061010.apk",
                url = "https://example.com/universal.apk"
            )
        )

        val selected = UpdateManifestSelector.selectArtifact(
            artifacts,
            supportedAbis = listOf("arm64-v8a")
        )

        assertEquals("legado_app_3.26.061010.apk", selected?.fileName)
    }

    @Test
    fun compareVersionName_comparesNumericParts() {
        assertTrue(UpdateManifestSelector.compareVersionName("3.26.061010", "3.26.060923") > 0)
        assertEquals(0, UpdateManifestSelector.compareVersionName("3.26.061010", "3.26.061010"))
        assertEquals(0, UpdateManifestSelector.compareVersionName("3.26.061010debug", "3.26.061010"))
        assertTrue(UpdateManifestSelector.compareVersionName("3.26.061010", "3.26.061011") < 0)
    }

    @Test
    fun parseVersionName_readsCurrentApkNamingWithoutDroppingDigits() {
        assertEquals(
            "3.26.061010",
            UpdateManifestSelector.parseVersionName("legado_app_3.26.061010_arm64-v8a.apk")
        )
        assertEquals(
            "3.26.061010",
            UpdateManifestSelector.parseVersionName("legado_app_3.26.061010_armeabi-v7a.apk")
        )
    }

    @Test
    fun toUpdateInfo_returnsSelectedAbiDownload() {
        val manifest = UpdateManifest(
            versionName = "3.26.061010",
            updateLog = "更新日志",
            artifacts = listOf(
                UpdateManifest.Artifact(
                    abi = "armeabi-v7a",
                    fileName = "legado_app_3.26.061010_armeabi-v7a.apk",
                    url = "https://example.com/v7.apk"
                ),
                UpdateManifest.Artifact(
                    abi = "arm64-v8a",
                    fileName = "legado_app_3.26.061010_arm64-v8a.apk",
                    url = "https://example.com/v8.apk"
                )
            )
        )

        val info = UpdateManifestSelector.toUpdateInfo(
            manifest,
            currentVersionName = "3.26.060923",
            supportedAbis = listOf("arm64-v8a")
        )

        assertNotNull(info)
        assertEquals("3.26.061010", info!!.tagName)
        assertEquals("更新日志", info.updateLog)
        assertEquals("https://example.com/v8.apk", info.downloadUrl)
        assertEquals("legado_app_3.26.061010_arm64-v8a.apk", info.fileName)
    }

    @Test
    fun toUpdateInfo_returnsNullWhenManifestIsNotNewer() {
        val manifest = UpdateManifest(
            versionName = "3.26.061010",
            artifacts = listOf(
                UpdateManifest.Artifact(
                    abi = "arm64-v8a",
                    fileName = "legado_app_3.26.061010_arm64-v8a.apk",
                    url = "https://example.com/v8.apk"
                )
            )
        )

        val info = UpdateManifestSelector.toUpdateInfo(
            manifest,
            currentVersionName = "3.26.061010debug",
            supportedAbis = listOf("arm64-v8a")
        )

        assertNull(info)
    }

    @Test
    fun githubAsset_acceptsUploadedApkEvenWhenContentTypeIsOctetStream() {
        val asset = Asset(
            apkUrl = "https://github.com/example/release.apk",
            contentType = "application/octet-stream",
            createdAt = "2026-06-10T00:00:00Z",
            downloadCount = 0,
            id = 1,
            name = "legado_app_3.26.061010_arm64-v8a.apk",
            state = "uploaded",
            url = "https://api.github.com/assets/1"
        )

        assertTrue(asset.isValid)
    }

    @Test
    fun githubAsset_preReleaseWithoutReleaseInNameIsBeta() {
        val asset = Asset(
            apkUrl = "https://github.com/example/legado_app_3.26.061010_arm64-v8a.apk",
            contentType = "application/octet-stream",
            createdAt = "2026-06-10T00:00:00Z",
            downloadCount = 0,
            id = 1,
            name = "legado_app_3.26.061010_arm64-v8a.apk",
            state = "uploaded",
            url = "https://api.github.com/assets/1"
        )

        assertEquals(AppVariant.BETA_RELEASE, asset.assetToAppReleaseInfo(true, "log").appVariant)
    }
}
