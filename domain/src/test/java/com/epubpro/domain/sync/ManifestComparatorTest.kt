package com.epubpro.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestComparatorTest {
    @Test
    fun localOnlyFileWithoutBaseline_isUploaded() {
        val local = manifest(mapOf("novels/a.html" to entry("a")))
        val result = ManifestComparator.compare(local, manifest(), null)

        assertEquals(SyncChangeType.UPLOAD, result.changes.single().type)
    }

    @Test
    fun driveOnlyFileWithoutBaseline_isDownloaded() {
        val drive = manifest(mapOf("uploads/a.bin" to entry("a")))
        val result = ManifestComparator.compare(manifest(), drive, null)

        assertEquals(SyncChangeType.DOWNLOAD, result.changes.single().type)
    }

    @Test
    fun bothSidesChangedFromBaseline_areConflict() {
        val baseline = manifest(mapOf("novels/a.html" to entry("base")))
        val local = manifest(mapOf("novels/a.html" to entry("local")))
        val drive = manifest(mapOf("novels/a.html" to entry("drive")))

        val result = ManifestComparator.compare(local, drive, baseline)

        assertEquals(SyncChangeType.CONFLICT, result.changes.single().type)
        assertEquals(listOf("novels/a.html"), result.blockingKeys)
    }

    @Test
    fun changedLocalAgainstUnchangedDrive_isUploaded() {
        val baseline = manifest(mapOf("novels/a.html" to entry("base")))
        val local = manifest(mapOf("novels/a.html" to entry("local")))

        val result = ManifestComparator.compare(local, baseline, baseline)

        assertEquals(SyncChangeType.UPLOAD, result.changes.single().type)
    }

    @Test
    fun databaseChangedOnBothSides_isBlockingConflict() {
        val baseline = manifest(database = databaseEntry("base"))
        val local = manifest(database = databaseEntry("local"))
        val drive = manifest(database = databaseEntry("drive"))

        val result = ManifestComparator.compare(local, drive, baseline)

        assertEquals(SyncChangeType.CONFLICT, result.databaseChange)
        assertTrue(result.blockingKeys.contains("database/local_db.sqlite3"))
    }

    @Test
    fun pathTraversalAndUnknownBucket_areRejected() {
        assertFalse(ManifestComparator.isAllowedStorageKey("novels/../secret"))
        assertFalse(ManifestComparator.isAllowedStorageKey("/novels/a"))
        assertFalse(ManifestComparator.isAllowedStorageKey("database/a"))
        assertTrue(ManifestComparator.isAllowedStorageKey("uploads/a.bin"))
    }

    private fun manifest(
        storage: Map<String, SyncFileEntry> = emptyMap(),
        database: SyncDatabaseEntry? = null
) = SyncManifest(
        schemaVersion = 1,
        createdAt = "2026-09-04T00:00:00Z",
        machine = "test",
        storage = storage,
        database = database
    )

    private fun entry(hash: String) = SyncFileEntry(1, 0, hash)

    private fun databaseEntry(hash: String) = SyncDatabaseEntry(1, 0, hash, hash)
}
