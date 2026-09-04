package com.epubpro.feature.profile.auth

import com.epubpro.domain.model.AuthProvider
import com.epubpro.domain.model.AuthState
import com.epubpro.domain.model.User
import com.epubpro.domain.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

/**
 * Kiểm thử đơn vị cho [AuthViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAuthRepository: AuthRepository
    private val authStateFlow = MutableStateFlow<AuthState>(AuthState.Unauthenticated)

    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAuthRepository = mock()
        whenever(mockAuthRepository.authState).thenReturn(authStateFlow)
        viewModel = AuthViewModel(mockAuthRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialUiStateHasDefaultCredentials() {
        val state = viewModel.uiState.value
        assertEquals("haiduog@gmail.com", state.email)
        assertEquals("123456", state.password)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun testFormInputChanges() {
        viewModel.onEmailChanged("custom@epubpro.app")
        viewModel.onPasswordChanged("654321")
        viewModel.togglePasswordVisibility()

        val state = viewModel.uiState.value
        assertEquals("custom@epubpro.app", state.email)
        assertEquals("654321", state.password)
        assertTrue(state.isPasswordVisible)
    }

    @Test
    fun testLoginSuccessSendsEffect() = runTest {
        val testUser = User(
            id = "u1",
            email = "haiduog@gmail.com",
            displayName = "Haiduog",
            provider = AuthProvider.EMAIL
        )
        whenever(mockAuthRepository.login(any(), any())).thenReturn(Result.success(testUser))

        viewModel.login()

        advanceUntilIdle()

        val effect = viewModel.effects.first()
        assertTrue(effect is AuthUiEffect.LoginSuccess)
        assertEquals("Haiduog", (effect as AuthUiEffect.LoginSuccess).user.displayName)
    }

    @Test
    fun testLoginFailureShowsErrorMessage() = runTest {
        whenever(mockAuthRepository.login(any(), any()))
            .thenReturn(Result.failure(IllegalArgumentException("Email không đúng.")))

        viewModel.onEmailChanged("wrong@example.com")
        viewModel.onPasswordChanged("wrongpass")
        viewModel.login()

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.errorMessage)
        assertEquals("Email không đúng.", state.errorMessage)
    }
    @Test
    fun testGoogleLoginSuccessSendsEffect() = runTest {
        val testUser = User(
            id = "google-u1",
            email = "google@example.com",
            displayName = "Google User",
            provider = AuthProvider.GOOGLE
        )
        whenever(mockAuthRepository.loginWithGoogle("id-token", "google@example.com", "Google User"))
            .thenReturn(Result.success(testUser))

        viewModel.loginWithGoogle("id-token", "google@example.com", "Google User")
        advanceUntilIdle()

        val effect = viewModel.effects.first()
        assertTrue(effect is AuthUiEffect.LoginSuccess)
        assertEquals("google@example.com", (effect as AuthUiEffect.LoginSuccess).user.email)
    }

}
