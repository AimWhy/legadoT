package io.legado.app.data.entities

import androidx.room.Entity

/** Maps a book-scoped alias to the canonical role identity used for casting. */
@Entity(tableName = "roleAliases", primaryKeys = ["bookUrl", "aliasName"])
data class RoleAlias(
    val bookUrl: String = "",
    val aliasName: String = "",
    val canonicalName: String = ""
)
