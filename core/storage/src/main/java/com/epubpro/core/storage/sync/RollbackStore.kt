package com.epubpro.core.storage.sync

import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Một bản ghi file đã tồn tại trước khi restore. */
data class RollbackEntry(
    val key: String,
    val backupFile: File?,
    val targetFile: File
)

/** Snapshot rollback cho file và database trước khi ghi đè. */
data class RollbackSnapshot(
    val directory: File,
    val entries: List<RollbackEntry>,
    val databaseBackup: File?
)

/**
 * Lưu rollback trong app-private storage và chỉ dọn sau khi sync thành công.
 */
@Singleton
class RollbackStore @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val local: LocalSnapshotDataSource
) {
    private val root = File(context.filesDir, "sync-rollback")

    /**
     * Tạo rollback trước khi restore hoặc force.
     *
     * @param keys Các key có thể bị ghi đè hoặc xóa.
     * @param includeDatabase Có sao lưu database hay không.
     * @return Snapshot rollback có thể dùng để khôi phục.
     */
    suspend fun create(keys: Collection<String>, includeDatabase: Boolean): RollbackSnapshot =
        withContext(Dispatchers.IO) {
            val directory = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            val entries = keys.distinct().map { key ->
                val target = local.targetFile(key)
                val backup = target.takeIf(File::isFile)?.let {
                    File(directory, "files/${safeName(key)}").also { destination ->
                        destination.parentFile?.mkdirs()
                        target.copyTo(destination, overwrite = true)
                    }
                }
                RollbackEntry(key, backup, target)
            }
            val databaseBackup = if (includeDatabase) {
                val source = context.getDatabasePath("epubpro.db")
                source.takeIf(File::isFile)?.let {
                    File(directory, "database/epubpro.db").also { destination ->
                        destination.parentFile?.mkdirs()
                        source.copyTo(destination, overwrite = true)
                    }
                }
            } else null
            RollbackSnapshot(directory, entries, databaseBackup)
        }

    /**
     * Khôi phục snapshot rollback vào local.
     *
     * @param snapshot Snapshot cần khôi phục.
     */
    suspend fun restore(snapshot: RollbackSnapshot) = withContext(Dispatchers.IO) {
        snapshot.entries.forEach { entry ->
            entry.targetFile.delete()
            entry.backupFile?.let { backup ->
                entry.targetFile.parentFile?.mkdirs()
                backup.copyTo(entry.targetFile, overwrite = true)
            }
        }
        snapshot.databaseBackup?.let { backup ->
            val target = context.getDatabasePath("epubpro.db")
            backup.copyTo(target, overwrite = true)
            File(target.path + "-wal").delete()
            File(target.path + "-shm").delete()
        }
    }

    /**
     * Dọn rollback sau khi phiên sync đã xác nhận thành công.
     *
     * @param snapshot Snapshot không còn cần giữ.
     */
    suspend fun cleanup(snapshot: RollbackSnapshot) = withContext(Dispatchers.IO) {
        snapshot.directory.deleteRecursively()
    }

    private fun safeName(key: String): String = key.replace('/', '_').replace('\\', '_')
}
