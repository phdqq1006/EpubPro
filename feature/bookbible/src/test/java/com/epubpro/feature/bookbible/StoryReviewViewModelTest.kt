package com.epubpro.feature.bookbible

import androidx.lifecycle.SavedStateHandle
import com.epubpro.domain.model.BookBibleReviewEvent
import com.epubpro.domain.model.BookBibleReviewEventEdit
import com.epubpro.domain.repository.BookBibleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

/**
 * Kiểm thử state và thao tác duyệt của [StoryReviewViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StoryReviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: BookBibleRepository

    private val pendingEvent = BookBibleReviewEvent(
        eventId = "event_1",
        bookId = "book_1",
        characterId = "char_1",
        characterOriginalName = "Lâm Phong",
        canonicalChapter = 4,
        category = "cultivation",
        attributeKey = "realm",
        operation = "set",
        valueJson = "{\"realm\":\"Trúc Cơ\"}",
        displayValue = "Trúc Cơ",
        evidence = "Đột phá cảnh giới"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock(BookBibleRepository::class.java)
        runBlocking {
            `when`(repository.getReviewEvents("book_1", "pending", null))
                .thenReturn(Result.success(listOf(pendingEvent)))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadPendingEventsAndApproveRemovesEventFromQueue() = runTest {
        `when`(repository.approveReviewEvent("event_1", BookBibleReviewEventEdit()))
            .thenReturn(Result.success(pendingEvent.copy(status = "approved")))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(pendingEvent), viewModel.uiState.value.events)

        viewModel.approve(pendingEvent)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.events.isEmpty())
        assertEquals(StoryReviewMessage.APPROVED, viewModel.uiState.value.message)
        verify(repository).approveReviewEvent("event_1", BookBibleReviewEventEdit())
    }

    @Test
    fun updateEventKeepsEventInQueueWithBackendValue() = runTest {
        val updatedEvent = pendingEvent.copy(displayValue = "Kim Đan")
        val edit = BookBibleReviewEventEdit(
            valueJson = "{\"realm\":\"Kim Đan\"}",
            evidence = "Bằng chứng mới",
            confidence = 0.9
        )
        `when`(repository.updateReviewEvent("event_1", edit))
            .thenReturn(Result.success(updatedEvent))

        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.update(pendingEvent, edit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(updatedEvent), viewModel.uiState.value.events)
        assertEquals(StoryReviewMessage.UPDATED, viewModel.uiState.value.message)
        verify(repository).updateReviewEvent("event_1", edit)
    }

    /**
     * Tạo ViewModel với argument bookId giống navigation thực tế.
     *
     * @return ViewModel đang theo dõi sách book_1.
     */
    private fun createViewModel(): StoryReviewViewModel {
        return StoryReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("bookId" to "book_1")),
            bookBibleRepository = repository
        )
    }
}
