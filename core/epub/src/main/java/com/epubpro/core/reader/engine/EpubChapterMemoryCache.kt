package com.epubpro.core.reader.engine

import androidx.collection.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bộ nhớ đệm RAM giới hạn theo dung lượng byte (Byte-Budget Memory Cache) cho nội dung HTML các chương sách EPUB đã được chuẩn hóa.
 *
 * Sử dụng [LruCache] với hạn mức bộ nhớ byte (mặc định 4 MiB) và cơ chế chống nạp trùng lặp đồng thời (single-flight loader)
 * để tránh việc Reader UI và TTS Service cùng giải nén và phân tích lại một entry EPUB giống nhau tại một thời điểm.
 */
@Singleton
class EpubChapterMemoryCache @Inject constructor() {

    private val singleFlightLocks = ConcurrentHashMap<String, Mutex>()

    /**
     * LRU cache lưu trữ HTML đã chuẩn hóa theo key chuỗi định danh.
     * Dung lượng được tính theo số byte xấp xỉ của chuỗi (String.length * 2).
     */
    private val lruCache = object : LruCache<String, String>(DEFAULT_MAX_BYTES) {
        override fun sizeOf(key: String, value: String): Int {
            return value.length * 2
        }
    }

    private val lock = Any()

    /**
     * Lấy nội dung HTML đã chuẩn hóa từ RAM cache nếu tồn tại.
     *
     * @param fingerprint Dấu vân tay định danh của tệp EPUB.
     * @param entryName Tên entry tệp chương trong tệp zip.
     * @param normalizerVersion Phiên bản của bộ chuẩn hóa HTML.
     * @return Chuỗi HTML đã chuẩn hóa nếu trúng cache (hit), ngược lại trả về `null`.
     */
    fun get(fingerprint: EpubCacheFingerprint, entryName: String, normalizerVersion: Int = CURRENT_NORMALIZER_VERSION): String? {
        val cacheKey = buildCacheKey(fingerprint, entryName, normalizerVersion)
        synchronized(lock) {
            return lruCache.get(cacheKey)
        }
    }

    /**
     * Lưu trữ nội dung HTML đã chuẩn hóa vào RAM cache.
     *
     * @param fingerprint Dấu vân tay định danh của tệp EPUB.
     * @param entryName Tên entry tệp chương trong tệp zip.
     * @param html Chuỗi HTML đã chuẩn hóa.
     * @param normalizerVersion Phiên bản của bộ chuẩn hóa HTML.
     */
    fun put(fingerprint: EpubCacheFingerprint, entryName: String, html: String, normalizerVersion: Int = CURRENT_NORMALIZER_VERSION) {
        val cacheKey = buildCacheKey(fingerprint, entryName, normalizerVersion)
        synchronized(lock) {
            lruCache.put(cacheKey, html)
        }
    }

    /**
     * Thực thi nạp dữ liệu chương với cơ chế Single-Flight: nếu nhiều luồng cùng yêu cầu một chương,
     * chỉ một luồng thực hiện đọc/chuẩn hóa dữ liệu thật sự, các luồng khác sẽ đợi và nhận kết quả từ cache.
     *
     * @param fingerprint Dấu vân tay định danh của tệp EPUB.
     * @param entryName Tên entry tệp chương.
     * @param loader Lambda thực thi đọc và chuẩn hóa dữ liệu từ đĩa nếu cache miss.
     * @return Chuỗi HTML chương đã chuẩn hóa.
     */
    suspend fun getOrLoad(
        fingerprint: EpubCacheFingerprint,
        entryName: String,
        normalizerVersion: Int = CURRENT_NORMALIZER_VERSION,
        loader: suspend () -> String
    ): String {
        val cacheKey = buildCacheKey(fingerprint, entryName, normalizerVersion)

        // Kiểm tra nhanh RAM cache
        synchronized(lock) {
            lruCache.get(cacheKey)?.let { return it }
        }

        // Lấy Mutex cho key cụ thể để tránh nạp trùng đồng thời
        val keyMutex = singleFlightLocks.computeIfAbsent(cacheKey) { Mutex() }
        return try {
            keyMutex.withLock {
                // Kiểm tra lại sau khi đã chiếm lock
                synchronized(lock) {
                    lruCache.get(cacheKey)?.let { return@withLock it }
                }

                val loadedHtml = loader()
                synchronized(lock) {
                    lruCache.put(cacheKey, loadedHtml)
                }
                loadedHtml
            }
        } finally {
            singleFlightLocks.remove(cacheKey, keyMutex)
        }
    }

    /**
     * Xóa toàn bộ các chương của một cuốn sách khỏi RAM cache khi sách bị xóa hoặc file bị sửa đổi.
     *
     * @param canonicalPath Đường dẫn chuẩn hóa của tệp sách cần dọn dẹp.
     */
    fun evictBook(canonicalPath: String) {
        synchronized(lock) {
            val prefix = canonicalPath + CACHE_KEY_SEPARATOR
            val snapshot = lruCache.snapshot()
            for (key in snapshot.keys) {
                if (key.startsWith(prefix)) {
                    lruCache.remove(key)
                }
            }
        }
    }

    /**
     * Xóa sạch toàn bộ RAM cache chương sách.
     */
    fun clear() {
        synchronized(lock) {
            lruCache.evictAll()
        }
        singleFlightLocks.clear()
    }

    private fun buildCacheKey(fingerprint: EpubCacheFingerprint, entryName: String, normalizerVersion: Int): String {
        return "${fingerprint.canonicalPath}$CACHE_KEY_SEPARATOR${fingerprint.fileLength}$CACHE_KEY_SEPARATOR${fingerprint.lastModified}$CACHE_KEY_SEPARATOR$entryName$CACHE_KEY_SEPARATOR$normalizerVersion"
    }

    companion object {
        /** Dung lượng RAM tối đa mặc định cho cache HTML chương: 4 MiB. */
        const val DEFAULT_MAX_BYTES = 4 * 1024 * 1024

        /** Phiên bản logic chuẩn hóa HTML. */
        const val CURRENT_NORMALIZER_VERSION = 1

        private const val CACHE_KEY_SEPARATOR = "::"
    }
}
