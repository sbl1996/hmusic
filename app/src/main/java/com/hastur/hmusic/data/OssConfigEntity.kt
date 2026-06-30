package com.hastur.hmusic.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "oss_config")
data class OssConfigEntity(
    @PrimaryKey val id: Int = 1,
    val endpoint: String = "",
    @ColumnInfo(defaultValue = "''")
    val region: String = "",
    @ColumnInfo(defaultValue = "0")
    val forcePathStyle: Boolean = false,
    val accessKeyId: String = "",
    val accessKeySecret: String = "",
    val bucket: String = "",
    val prefix: String = ""
)
