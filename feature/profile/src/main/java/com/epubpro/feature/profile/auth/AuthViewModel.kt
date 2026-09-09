package com.epubpro.feature.profile.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.domain.model.User
import com.epubpro.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Trạng thái giao diện của màn hình Xác thực Đăng nhập.
 */
data class AuthUiState(
    val email: String = "haiduog@gmail.com",
    val password: String = "123456",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isForgotPasswordDialogOpen: Boolean = false,
    val forgotPasswordEmail: String = "haiduog@gmail.com",
    val forgotPasswordSuccessMessage: String? = null,
    val isForgotPasswordLoading: Boolean = false
)

/**
 * Các sự kiện điều hướng hoặc thông báo phản hồi (One-shot UI Effect) từ AuthViewModel.
 */
sealed class AuthUiEffect {
    /** Đăng nhập thành công */
    data class LoginSuccess(val user: User) : AuthUiEffect()

    /** Hiển thị thông báo Snackbar/Toast */
    data class ShowMessage(val message: String) : AuthUiEffect()
}

/**
 * ViewModel phụ trách quản lý logic xác thực người dùng cho màn hình Đăng nhập.
 *
 * @param authRepository Đối tượng Repository cung cấp các phương thức xác thực tài khoản.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AuthUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /**
     * Cập nhật địa chỉ email người dùng đang nhập vào biểu mẫu.
     *
     * @param email Chuỗi ký tự email mới.
     */
    fun onEmailChanged(email: String) {
        _uiState.update { it.copy(email = email, errorMessage = null) }
    }

    /**
     * Cập nhật mật khẩu người dùng đang nhập vào biểu mẫu.
     *
     * @param password Chuỗi ký tự mật khẩu mới.
     */
    fun onPasswordChanged(password: String) {
        _uiState.update { it.copy(password = password, errorMessage = null) }
    }

    /**
     * Chuyển đổi trạng thái ẩn hoặc hiện mật khẩu trên giao diện.
     */
    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * Mở hộp thoại Khôi phục mật khẩu.
     */
    fun openForgotPasswordDialog() {
        _uiState.update {
            it.copy(
                isForgotPasswordDialogOpen = true,
                forgotPasswordEmail = it.email,
                forgotPasswordSuccessMessage = null
            )
        }
    }

    /**
     * Đóng hộp thoại Khôi phục mật khẩu.
     */
    fun closeForgotPasswordDialog() {
        _uiState.update {
            it.copy(
                isForgotPasswordDialogOpen = false,
                forgotPasswordSuccessMessage = null
            )
        }
    }

    /**
     * Cập nhật email trong hộp thoại Khôi phục mật khẩu.
     *
     * @param email Chuỗi email cần nhận liên kết đặt lại.
     */
    fun onForgotPasswordEmailChanged(email: String) {
        _uiState.update { it.copy(forgotPasswordEmail = email) }
    }

    /**
     * Gửi yêu cầu đặt lại mật khẩu tới email người dùng đã nhập.
     */
    fun submitForgotPassword() {
        val targetEmail = _uiState.value.forgotPasswordEmail.trim()
        if (targetEmail.isBlank()) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isForgotPasswordLoading = true) }
            val result = authRepository.sendPasswordResetEmail(targetEmail)
            _uiState.update { it.copy(isForgotPasswordLoading = false) }

            result.onSuccess {
                _uiState.update {
                    it.copy(forgotPasswordSuccessMessage = targetEmail)
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Không thể gửi yêu cầu khôi phục.")
                }
            }
        }
    }

    /**
     * Xử lý thực hiện Đăng nhập tài khoản bằng Email và Mật khẩu.
     */
    fun login() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password.trim()

        if (email.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập địa chỉ email.") }
            return
        }
        if (password.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Vui lòng nhập mật khẩu.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.login(email, password)
            _uiState.update { it.copy(isLoading = false) }

            result.onSuccess { user ->
                _effects.send(AuthUiEffect.LoginSuccess(user))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message ?: "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin.")
                }
            }
        }
    }

    /**
     * Hoàn tất đăng nhập bằng tài khoản Google sau khi Google Sign-In trả kết quả.
     *
     * @param idToken ID token Google nếu OAuth client đã cấu hình.
     * @param email Email tài khoản Google.
     * @param displayName Tên hiển thị tài khoản Google.
     */
    fun loginWithGoogle(idToken: String?, email: String?, displayName: String?) {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = authRepository.loginWithGoogle(idToken, email, displayName)
            _uiState.update { it.copy(isLoading = false) }
            result.onSuccess { user ->
                _effects.send(AuthUiEffect.LoginSuccess(user))
            }.onFailure { error ->
                _uiState.update {
                    it.copy(errorMessage = error.message)
                }
            }
        }
    }
    /**
     * Hiển thị lỗi khi Google Sign-In bị hủy hoặc trả về kết quả không hợp lệ.
     */
    fun showGoogleSignInError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }
}
