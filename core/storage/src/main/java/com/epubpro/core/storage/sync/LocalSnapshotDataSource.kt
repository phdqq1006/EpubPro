package com.epubpro.core.storage.sync

import androidx.room.RoomDatabase
import com.epubpro.core.database.AppDatabase
import com.epubpro.domain.sync.SyncDatabaseEntry
import com.epubpro.domain.sync.SyncFileEntry
import com.epubpro.domain.sync.SyncManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Snapshot local gồm manifest và các file nguồn đã hash. */
data class LocalSyncSnapshot(
    val manifest: SyncManifest,
    val files: Map<String, File>,
    val databaseFile: File
)

/**
 * Adapter duy nhất đọc/ghi dữ liệu local cho sync.
 *
 * File legacy trong `filesDir/books` được ánh xạ thành bucket `novels` để không làm mất dữ liệu
 * đã import trước khi tính năng sync được thêm vào. Các file mới theo contract nằm trong
 * `filesDir/storage/novels` và `filesDir/storage/uploads`.
 */
@Singleton
class LocalSnapshotDataSource @Inject constructor(
    @ApplicationContext private val context: android.content.Context,
    private val appDatabase: AppDatabase
) {
    private val syncRoot = File(context.filesDir, "sync-snapshots")
    private val storageRoot = File(context.filesDir, "storage")
    private val databaseName = "epubpro.db"

    /**
     * Tạo snapshot local trên dispatcher I/O.
     *
     * @return Snapshot có manifest, file map và database snapshot ổn định.
     */
    suspend fun createSnapshot(): LocalSyncSnapshot = withContext(Dispatchers.IO) {
        syncRoot.mkdirs()
        cleanupTemporaryFiles()
        val databaseFile = createDatabaseSnapshot()
        val files = linkedMapOf<String, File>()
        scanBucket("novels", novelsRoot(), files)
        scanBucket("uploads", File(storageRoot, "uploads"), files)
        val manifest = SyncManifest(
            schemaVersion = ManifestJsonCodec.SCHEMA_VERSION,
            createdAt = Instant.now().toString(),
            machine = machineId(),
            storage = files.mapValues { (_, file) -> fileEntry(file) },
            database = databaseEntry(databaseFile)
        )
        LocalSyncSnapshot(manifest, files, databaseFile)
    }

    /**
     * Trả về file local đích cho một key đã được validate.
     *
     * @param key Relative key dạng POSIX.
     * @return File đích trong app-private storage.
     * @throws IllegalArgumentException Nếu key nằm ngoài bucket cho phép.
     */
    fun targetFile(key: String): File {
        require(key.startsWith("novels/") || key.startsWith("uploads/")) { "Key local không hợp lệ" }
        val bucket = key.substringBefore('/')
        val relativePath = key.substringAfter('/')
        require(relativePath.isNotBlank() && relativePath.split('/').none { it == ".." || it.isBlank() }) {
            "Key local không hợp lệ"
        }
        return File(File(storageRoot, bucket), relativePath)
    }

    /**
     * Commit file tạm sau khi caller đã kiểm tra checksum.
     *
     * @param key Relative key cần ghi.
     * @param temporary File tạm cùng filesystem với file đích.
     */
    suspend fun commitFile(key: String, temporary: File) = withContext(Dispatchers.IO) {
        val target = targetFile(key)
        target.parentFile?.mkdirs()
        if (target.exists() && !target.delete()) error("Không thể thay thế file sync local")
        check(temporary.renameTo(target)) { "Không thể commit file sync local" }
    }

    /**
     * Xóa một file local theo key đã validate.
     *
     * @param key Relative key cần xóa.
     */
    suspend fun deleteFile(key: String) = withContext(Dispatchers.IO) {
        targetFile(key).delete()
    }

    /**
     * Thay thế database bằng snapshot đã kiểm tra checksum.
     *
     * Room sẽ mở lại SQLite helper khi truy cập DAO sau khi database được đóng.
     *
     * @param temporary Snapshot database tạm.
     */
    suspend fun replaceDatabase(temporary: File) = withContext(Dispatchers.IO) {
        appDatabase.close()
        val target = context.getDatabasePath(databaseName)
        target.parentFile?.mkdirs()
        val previous = File(target.parentFile, "$databaseName.previous")
        previous.delete()
        if (target.exists()) check(target.renameTo(previous)) { "Không thể chuẩn bị database local" }
        if (!temporary.renameTo(target)) {
            previous.renameTo(target)
            error("Không thể restore database local")
        }
        previous.delete()
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
    }

    /**
     * Tạo tên file tạm cho download.
     *
     * @param key Relative key cần download.
     * @return File có hậu tố `.syncing`.
     */
    fun createTemporaryFile(key: String): File {
        val target = targetFile(key)
        target.parentFile?.mkdirs()
        return File(target.parentFile, "${target.name}.${UUID.randomUUID()}.syncing")
    }

    /**
     * ^�o file ~�m database trong app-private storage � c� t~� rename atomic.
     *
     * @return File ~�m c� h�u ~� `.syncing`.
     */
    fun createTemporaryDatabaseFile(): File =
        File(syncRoot, "database_${UUID.randomUUID()}.sqlite3.syncing").also { it.parentFile?.mkdirs() }

    private fun createDatabaseSnapshot(): File {
        val database = appDatabase.openHelper.writableDatabase
        database.query("PRAGMA wal_checkpoint(FULL)").use { }
        val source = context.getDatabasePath(databaseName)
        check(source.isFile) { "Không tìm thấy database local" }
        val target = File(syncRoot, "${UUID.randomUUID()}_$databaseName.syncing")
        source.copyTo(target, overwrite = true)
        val stableTarget = File(target.parentFile, target.name.removeSuffix(".syncing"))
        check(target.renameTo(stableTarget)) { "Không thể tạo database snapshot" }
        return stableTarget
    }

    private fun scanBucket(bucket: String, root: File, files: MutableMap<String, File>) {
        if (!root.isDirectory) return
        root.walkTopDown()
            .filter { it.isFile && !it.name.endsWith(".syncing") && !it.name.endsWith(".tmp") }
            .forEach { file ->
                val relative = root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/')
                files["$bucket/$relative"] = file
            }
    }

    private fun novelsRoot(): File {
        val contractRoot = File(storageRoot, "novels")
        return if (contractRoot.isDirectory && contractRoot.walkTopDown().any { it.isFile }) {
            contractRoot
        } else {
            File(context.filesDir, "books")
        }
    }

    private fun fileEntry(file: File): SyncFileEntry = SyncFileEntry(
        size = file.length(),
        mtimeNs = file.lastModified().coerceAtLeast(0L) * 1_000_000L,
        sha256 = sha256(file)
    )

    private fun databaseEntry(file: File): SyncDatabaseEntry {
        val hash = sha256(file)
        return SyncDatabaseEntry(file.length(), file.lastModified().coerceAtLeast(0L) * 1_000_000L, hash, null)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun cleanupTemporaryFiles() {
        syncRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".syncing") }
            .forEach(File::delete)
    }

    private fun machineId(): String =
        android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "android-${UUID.randomUUID()}"
}
