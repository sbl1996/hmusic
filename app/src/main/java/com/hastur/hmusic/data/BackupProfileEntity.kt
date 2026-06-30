package com.hastur.hmusic.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "backup_profiles",
    indices = [Index(value = ["name"], unique = true)]
)
data class BackupProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    @ColumnInfo(defaultValue = "''")
    val endpoint: String = "",
    @ColumnInfo(defaultValue = "''")
    val region: String = "",
    @ColumnInfo(defaultValue = "0")
    val forcePathStyle: Boolean = false,
    val accessKeyId: String = "",
    val accessKeySecret: String = "",
    val bucket: String = "",
    val prefix: String = "",
    @ColumnInfo(defaultValue = "0")
    val isActive: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val lastSyncAt: Long = 0
)
