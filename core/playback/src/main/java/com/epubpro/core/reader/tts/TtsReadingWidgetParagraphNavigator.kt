package com.epubpro.core.reader.tts

/**
 * Bộ tính toán thuần túy xác định vị trí chỉ số chương (chapterIndex) và chỉ số đoạn văn (paragraphIndex)
 * cho các thao tác di chuyển câu/đoạn trên Home Screen Reading Widget.
 *
 * Hỗ trợ chuyển tiếp tràn/thiếu (overflow/underflow carry-over) xuyên qua các ranh giới chương
 * mà không bị reset về 0 hoặc vị trí cuối cùng một cách khiên cưỡng.
 */
object TtsReadingWidgetParagraphNavigator {

    /**
     * Trạng thái kết quả di chuyển vị trí đọc trên Widget.
     *
     * @property chapterIndex Chỉ số chương mục tiêu sau khi di chuyển.
     * @property paragraphIndex Chỉ số đoạn văn mục tiêu trong chương.
     */
    data class NavigationResult(
        val chapterIndex: Int,
        val paragraphIndex: Int
    )

    /**
     * Tính toán chỉ số chương và đoạn văn mục tiêu dựa trên khoảng di chuyển tương đối [relativeMove]
     * kết hợp cơ chế chuyển tiếp bước dư (overflow/underflow carry-over) qua ranh giới giữa các chương.
     *
     * @param currentChapterIndex Chỉ số chương hiện tại.
     * @param currentParagraphIndex Chỉ số đoạn văn hiện tại trong chương.
     * @param relativeMove Số bước đoạn văn cần di chuyển (+ cho tiến tới, - cho lùi lại).
     * @param totalChapters Tổng số chương hiện có trong cuốn sách.
     * @param getParagraphCount Hàm lấy tổng số đoạn văn của một chương chỉ định (trả về 0 nếu chương rỗng hoặc không tồn tại).
     * @return Đối tượng [NavigationResult] chứa chỉ số chương và đoạn văn mục tiêu mới.
     */
    suspend fun calculateTargetPosition(
        currentChapterIndex: Int,
        currentParagraphIndex: Int,
        relativeMove: Int,
        totalChapters: Int,
        getParagraphCount: suspend (chapterIndex: Int) -> Int
    ): NavigationResult {
        if (totalChapters <= 0) {
            return NavigationResult(chapterIndex = 0, paragraphIndex = 0)
        }

        var chapter = currentChapterIndex.coerceIn(0, totalChapters - 1)
        val targetParagraph = currentParagraphIndex + relativeMove

        if (relativeMove >= 0) {
            // Forward movement (Next)
            val currentCount = getParagraphCount(chapter)
            if (currentCount > 0 && targetParagraph < currentCount) {
                return NavigationResult(chapterIndex = chapter, paragraphIndex = targetParagraph)
            }

            var overflow = if (currentCount > 0) targetParagraph - currentCount else targetParagraph
            while (true) {
                if (chapter < totalChapters - 1) {
                    chapter++
                    val count = getParagraphCount(chapter)
                    if (count <= 0) {
                        // Empty chapter, skip to next
                        continue
                    }
                    if (overflow < count) {
                        // Landed inside this chapter
                        return NavigationResult(chapterIndex = chapter, paragraphIndex = overflow)
                    } else {
                        overflow -= count
                    }
                } else {
                    // Reached end of book
                    val lastCount = getParagraphCount(chapter)
                    return NavigationResult(chapterIndex = chapter, paragraphIndex = (lastCount - 1).coerceAtLeast(0))
                }
            }
        } else {
            // Backward movement (Previous)
            if (targetParagraph >= 0) {
                return NavigationResult(chapterIndex = chapter, paragraphIndex = targetParagraph)
            }

            var underflow = -targetParagraph
            while (true) {
                if (chapter > 0) {
                    chapter--
                    val count = getParagraphCount(chapter)
                    if (count <= 0) {
                        // Empty chapter, skip to previous
                        continue
                    }
                    if (underflow <= count) {
                        // Landed inside this chapter
                        return NavigationResult(chapterIndex = chapter, paragraphIndex = count - underflow)
                    } else {
                        underflow -= count
                    }
                } else {
                    // Reached beginning of book
                    return NavigationResult(chapterIndex = 0, paragraphIndex = 0)
                }
            }
        }
    }
}

