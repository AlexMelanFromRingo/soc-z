package io.melan.socz.collectors

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager

data class VolumeInfo(
    val label: String,
    val path: String,
    val totalBytes: Long,
    val availBytes: Long,
    val isPrimary: Boolean,
    val isRemovable: Boolean,
    val isEmulated: Boolean,
) {
    val usedBytes: Long get() = totalBytes - availBytes
}

object StorageCollector {
    fun read(ctx: Context): List<VolumeInfo> {
        val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val volumes = sm.storageVolumes
        return volumes.mapNotNull { v ->
            val dir = v.directory ?: return@mapNotNull null
            val stat = runCatching { StatFs(dir.absolutePath) }.getOrNull() ?: return@mapNotNull null
            VolumeInfo(
                label = v.getDescription(ctx) ?: dir.name,
                path = dir.absolutePath,
                totalBytes = stat.totalBytes,
                availBytes = stat.availableBytes,
                isPrimary = v.isPrimary,
                isRemovable = v.isRemovable,
                isEmulated = v.isEmulated,
            )
        }.ifEmpty {
            // Fallback if storageVolumes is empty
            val s = StatFs(Environment.getDataDirectory().absolutePath)
            listOf(VolumeInfo("Internal", "/data", s.totalBytes, s.availableBytes,
                isPrimary = true, isRemovable = false, isEmulated = true))
        }
    }
}
