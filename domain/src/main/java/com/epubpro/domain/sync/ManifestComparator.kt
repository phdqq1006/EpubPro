package com.epubpro.domain.sync

/**
 * So sánh nội dung theo SHA-256 và phân loại thay đổi dựa trên baseline.
 */
object ManifestComparator {
    /**
     * So sánh local, Drive và baseline theo từng key.
     *
     * @param local Manifest hiện tại trên thiết bị.
     * @param drive Manifest hiện tại trên Drive.
     * @param baseline Manifest cuối cùng đã xác nhận, có thể null ở lần đầu.
     * @return Kế hoạch thay đổi không gây side effect.
     */
    fun compare(
        local: SyncManifest,
        drive: SyncManifest,
        baseline: SyncManifest?
    ): SyncComparison {
        val keys = (local.storage.keys + drive.storage.keys + baseline.orEmpty().storage.keys).toSortedSet()
        val changes = keys.map { key ->
            SyncChange(key, classify(local.storage[key], drive.storage[key], baseline?.storage?.get(key)))
        }
        val databaseChange = classifyDatabase(local.database, drive.database, baseline?.database)
        return SyncComparison(changes, databaseChange)
    }

    /**
     * Kiểm tra một relative key có thuộc bucket được phép hay không.
     *
     * @param key Relative key dạng POSIX.
     * @return true nếu key an toàn và thuộc `novels` hoặc `uploads`.
     */
    fun isAllowedStorageKey(key: String): Boolean {
        if (key.isBlank() || key.startsWith('/') || key.contains("\\") || key.split('/').any { it == ".." || it.isBlank() }) {
            return false
        }
        return key.startsWith("novels/") || key.startsWith("uploads/")
    }

    private fun classify(
        local: SyncFileEntry?,
        drive: SyncFileEntry?,
        baseline: SyncFileEntry?
    ): SyncChangeType {
        if (same(local, drive)) return SyncChangeType.UNCHANGED
        if (baseline == null) {
            return when {
                local == null -> SyncChangeType.DOWNLOAD
                drive == null -> SyncChangeType.UPLOAD
                else -> SyncChangeType.CONFLICT
            }
        }
        val localChanged = !same(local, baseline)
        val driveChanged = !same(drive, baseline)
        return when {
            localChanged && driveChanged -> SyncChangeType.CONFLICT
            localChanged && drive == null -> SyncChangeType.DELETE_REMOTE
            localChanged -> SyncChangeType.UPLOAD
            driveChanged && local == null -> SyncChangeType.DELETE_LOCAL
            driveChanged -> SyncChangeType.DOWNLOAD
            else -> SyncChangeType.UNCHANGED
        }
    }

    private fun classifyDatabase(
        local: SyncDatabaseEntry?,
        drive: SyncDatabaseEntry?,
        baseline: SyncDatabaseEntry?
    ): SyncChangeType {
        if (local?.contentSha256 == null || drive?.contentSha256 == null) {
            return if (local == null && drive == null) SyncChangeType.UNCHANGED else SyncChangeType.INCOMPATIBLE
        }
        return when {
            same(local, drive) -> SyncChangeType.UNCHANGED
            baseline == null && local == null -> SyncChangeType.DOWNLOAD
            baseline == null && drive == null -> SyncChangeType.UPLOAD
            baseline == null -> SyncChangeType.CONFLICT
            !same(local, baseline) && !same(drive, baseline) -> SyncChangeType.CONFLICT
            !same(local, baseline) && drive == null -> SyncChangeType.DELETE_REMOTE
            !same(local, baseline) -> SyncChangeType.UPLOAD
            !same(drive, baseline) && local == null -> SyncChangeType.DELETE_LOCAL
            !same(drive, baseline) -> SyncChangeType.DOWNLOAD
            else -> SyncChangeType.UNCHANGED
        }
    }

    private fun same(left: SyncFileEntry?, right: SyncFileEntry?): Boolean =
        left == null && right == null || left?.sha256 == right?.sha256

    private fun same(left: SyncDatabaseEntry?, right: SyncDatabaseEntry?): Boolean =
        left == null && right == null || left?.contentSha256 == right?.contentSha256
}
