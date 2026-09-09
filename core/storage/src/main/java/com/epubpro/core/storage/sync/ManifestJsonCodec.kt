package com.epubpro.core.storage.sync

import com.epubpro.domain.sync.ManifestComparator
import com.epubpro.domain.sync.SyncDatabaseEntry
import com.epubpro.domain.sync.SyncFileEntry
import com.epubpro.domain.sync.SyncManifest
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chuyển đổi manifest giữa domain model và contract JSON của backend.
 *
 * Codec chỉ chấp nhận schema version 1 và các key thuộc `novels` hoặc `uploads`.
 */
@Singleton
class ManifestJsonCodec @Inject constructor(
    private val gson: Gson
) {
    /**
     * Đọc manifest JSON và kiểm tra schema, checksum cùng path.
     *
     * @param json JSON manifest nhận từ Drive hoặc local baseline.
     * @return Manifest hợp lệ.
     * @throws IllegalArgumentException Nếu JSON sai schema hoặc chứa dữ liệu không an toàn.
     */
    fun decode(json: String): SyncManifest {
        val dto = try {
            gson.fromJson(json, ManifestDto::class.java)
        } catch (error: JsonParseException) {
            throw IllegalArgumentException("Manifest JSON không hợp lệ", error)
        }
        require(dto != null && dto.schemaVersion == SCHEMA_VERSION) {
            "Manifest schema không tương thích"
        }
        val storage = dto.storage.orEmpty().mapValues { (key, value) ->
            require(ManifestComparator.isAllowedStorageKey(key)) { "Manifest chứa path không được phép" }
            value.toDomain(key)
        }
        return SyncManifest(
            schemaVersion = dto.schemaVersion,
            createdAt = dto.createdAt.orEmpty(),
            machine = dto.machine.orEmpty(),
            storage = storage,
            database = dto.database?.toDomain()
        )
    }

    /**
     * Ghi manifest theo đúng tên field backend yêu cầu.
     *
     * @param manifest Manifest cần serialize.
     * @return JSON UTF-8 không chứa token hoặc credential.
     */
    fun encode(manifest: SyncManifest): String {
        require(manifest.schemaVersion == SCHEMA_VERSION) { "Manifest schema không tương thích" }
        manifest.storage.keys.forEach { key ->
            require(ManifestComparator.isAllowedStorageKey(key)) { "Manifest chứa path không được phép" }
        }
        return gson.toJson(ManifestDto.fromDomain(manifest))
    }

    private fun FileDto.toDomain(key: String): SyncFileEntry {
        require(size >= 0L && mtimeNs >= 0L && sha256.isSha256()) { "Checksum không hợp lệ cho $key" }
        return SyncFileEntry(size, mtimeNs, sha256.lowercase())
    }

    private fun DatabaseDto.toDomain(): SyncDatabaseEntry {
        require(size >= 0L && mtimeNs >= 0L && sha256.isSha256()) { "Checksum database không hợp lệ" }
        require(contentSha256 == null || contentSha256.isSha256()) { "Fingerprint database không hợp lệ" }
        return SyncDatabaseEntry(size, mtimeNs, sha256.lowercase(), contentSha256?.lowercase())
    }

    private fun String.isSha256(): Boolean = matches(Regex("[0-9a-fA-F]{64}"))

    private data class ManifestDto(
        @SerializedName("schema_version") val schemaVersion: Int = 0,
        @SerializedName("created_at") val createdAt: String? = null,
        val machine: String? = null,
        val storage: Map<String, FileDto>? = null,
        val database: DatabaseDto? = null
    ) {
        companion object {
            /** Tạo DTO để serialize manifest domain. */
            fun fromDomain(manifest: SyncManifest): ManifestDto = ManifestDto(
                schemaVersion = manifest.schemaVersion,
                createdAt = manifest.createdAt,
                machine = manifest.machine,
                storage = manifest.storage.mapValues { (_, entry) -> FileDto.fromDomain(entry) },
                database = manifest.database?.let(DatabaseDto::fromDomain)
            )
        }
    }

    private data class FileDto(
        val size: Long = 0L,
        @SerializedName("mtime_ns") val mtimeNs: Long = 0L,
        val sha256: String = ""
    ) {
        companion object {
            fun fromDomain(entry: SyncFileEntry) = FileDto(entry.size, entry.mtimeNs, entry.sha256)
        }
    }

    private data class DatabaseDto(
        val size: Long = 0L,
        @SerializedName("mtime_ns") val mtimeNs: Long = 0L,
        val sha256: String = "",
        @SerializedName("content_sha256") val contentSha256: String? = null
    ) {
        companion object {
            fun fromDomain(entry: SyncDatabaseEntry) = DatabaseDto(
                entry.size,
                entry.mtimeNs,
                entry.sha256,
                entry.contentSha256
            )
        }
    }

    companion object {
        /** Version manifest đang tương thích với backend. */
        const val SCHEMA_VERSION = 1
    }
}
