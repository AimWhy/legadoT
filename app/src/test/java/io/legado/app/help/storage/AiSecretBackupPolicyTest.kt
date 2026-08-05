package io.legado.app.help.storage

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AiSecretBackupPolicyTest {

    @Test
    fun `api key and consent are automatically excluded from backup and restore`() {
        val source = listOf(
            File("src/main/java/io/legado/app/help/storage/BackupConfig.kt"),
            File("app/src/main/java/io/legado/app/help/storage/BackupConfig.kt")
        ).first { it.isFile }.readText()
        assertTrue(source.contains("PreferKey.aiApiKey"))
        assertTrue(source.contains("PreferKey.aiRoleConsent"))
    }
}
