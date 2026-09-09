package com.epubpro.domain.sync

/**
 * Cung cấp manifest rỗng cho phép ghép key khi baseline chưa tồn tại.
 *
 * @return Manifest rỗng nếu receiver null, ngược lại chính receiver.
 */
fun SyncManifest?.orEmpty(): SyncManifest = this ?: SyncManifest(
    schemaVersion = 1,
    createdAt = "",
    machine = "",
    storage = emptyMap(),
    database = null
)
