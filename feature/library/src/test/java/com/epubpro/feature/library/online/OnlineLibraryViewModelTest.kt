package com.epubpro.feature.library.online

import com.epubpro.domain.model.Book
import com.epubpro.domain.model.OnlineNovelSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineLibraryViewModelTest {

    /**
     * Kiểm tra định danh online đã lưu trong Room được ưu tiên hơn suy luận từ metadata.
     */
    @Test
    fun resolveDownloadedNovelIdsUsesPersistedIdentity() {
        val localBooks = listOf(
            Book(
                id = "local-1",
                title = "Tên local khác",
                author = "Tác giả khác",
                coverPath = null,
                filePath = "book.epub",
                addedAt = 1L,
                lastReadAt = 1L,
                onlineNovelId = "novel-1"
            )
        )

        val result = resolveDownloadedNovelIds(emptyList(), localBooks)

        assertEquals(setOf("novel-1"), result)
    }

    /**
     * Kiểm tra dữ liệu cũ chỉ được suy luận khi cặp tên sách và tác giả là duy nhất.
     */
    @Test
    fun resolveDownloadedNovelIdsInfersOnlyUniqueLegacyPair() {
        val novels = listOf(
            OnlineNovelSummary(novelId = "unique", title = "Truyện A", author = "Tác giả A"),
            OnlineNovelSummary(novelId = "duplicate-1", title = "Truyện B", author = "Tác giả B"),
            OnlineNovelSummary(novelId = "duplicate-2", title = "Truyện B", author = "Tác giả B")
        )
        val localBooks = listOf(
            Book(
                id = "local-a",
                title = " truyện a ",
                author = "TÁC GIẢ A",
                coverPath = null,
                filePath = "a.epub",
                addedAt = 1L,
                lastReadAt = 1L
            ),
            Book(
                id = "local-b",
                title = "Truyện B",
                author = "Tác giả B",
                coverPath = null,
                filePath = "b.epub",
                addedAt = 1L,
                lastReadAt = 1L
            )
        )

        val result = resolveDownloadedNovelIds(novels, localBooks)

        assertEquals(setOf("unique"), result)
    }

    /**
     * Kiểm tra bộ lọc kết hợp từ khóa không phân biệt hoa thường với thể loại đang chọn.
     */
    @Test
    fun filterOnlineNovelsCombinesQueryAndGenre() {
        val matchingNovel = OnlineNovelSummary(
            novelId = "matching",
            title = "Thần Kiếm",
            originalTitle = "Divine Sword",
            author = "An",
            genres = listOf("Tiên hiệp")
        )
        val wrongGenre = OnlineNovelSummary(
            novelId = "wrong-genre",
            title = "Thần Đao",
            author = "Bình",
            genres = listOf("Đô thị")
        )
        val wrongQuery = OnlineNovelSummary(
            novelId = "wrong-query",
            title = "Ma Pháp",
            author = "Chi",
            genres = listOf("Tiên hiệp")
        )

        val result = filterOnlineNovels(
            novels = listOf(matchingNovel, wrongGenre, wrongQuery),
            query = "divine",
            genre = "Tiên hiệp"
        )

        assertEquals(1, result.size)
        assertTrue(result.single() === matchingNovel)
    }
}
