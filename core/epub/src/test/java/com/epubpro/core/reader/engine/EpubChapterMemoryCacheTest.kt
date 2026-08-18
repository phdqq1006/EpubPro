package com.epubpro.core.reader.engine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class EpubChapterMemoryCacheTest {

    private lateinit var cache: EpubChapterMemoryCache
    private lateinit var dummyFingerprint: EpubCacheFingerprint

    @Before
    fun setup() {
        cache = EpubChapterMemoryCache()
        val tempFile = File.createTempFile("mem_cache_test", ".epub").apply {
            writeText("test")
            deleteOnExit()
        }
        dummyFingerprint = EpubCacheFingerprint.fromFile(tempFile)
    }

    @Test
    fun `get and put works in memory`() {
        assertNull(cache.get(dummyFingerprint, "c1.xhtml"))

        cache.put(dummyFingerprint, "c1.xhtml", "<p>Hello</p>")
        val cached = cache.get(dummyFingerprint, "c1.xhtml")
        assertNotNull(cached)
        assertEquals("<p>Hello</p>", cached)
    }

    @Test
    fun `getOrLoad only executes loader once on cache hit`() = runBlocking {
        val loadCount = AtomicInteger(0)

        val result1 = cache.getOrLoad(dummyFingerprint, "c1.xhtml") {
            loadCount.incrementAndGet()
            "<p>Loaded Content</p>"
        }

        val result2 = cache.getOrLoad(dummyFingerprint, "c1.xhtml") {
            loadCount.incrementAndGet()
            "<p>Loaded Content 2</p>"
        }

        assertEquals("<p>Loaded Content</p>", result1)
        assertEquals("<p>Loaded Content</p>", result2)
        assertEquals(1, loadCount.get())
    }

    @Test
    fun `single-flight deduplicates concurrent loads for same entry`() = runBlocking {
        val loadCount = AtomicInteger(0)

        val deferreds = (1..10).map {
            async {
                cache.getOrLoad(dummyFingerprint, "c1.xhtml") {
                    delay(50)
                    loadCount.incrementAndGet()
                    "<p>Concurrent Loaded Content</p>"
                }
            }
        }

        val results = deferreds.awaitAll()
        results.forEach { assertEquals("<p>Concurrent Loaded Content</p>", it) }
        assertEquals(1, loadCount.get())
    }

    @Test
    fun `evictBook removes all chapters of the specified book`() {
        cache.put(dummyFingerprint, "c1.xhtml", "<p>Chap 1</p>")
        cache.put(dummyFingerprint, "c2.xhtml", "<p>Chap 2</p>")

        assertNotNull(cache.get(dummyFingerprint, "c1.xhtml"))
        assertNotNull(cache.get(dummyFingerprint, "c2.xhtml"))

        cache.evictBook(dummyFingerprint.canonicalPath)

        assertNull(cache.get(dummyFingerprint, "c1.xhtml"))
        assertNull(cache.get(dummyFingerprint, "c2.xhtml"))
    }
}
