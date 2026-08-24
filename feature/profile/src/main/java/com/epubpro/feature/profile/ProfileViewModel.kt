package com.epubpro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.storage.ServerPreferencesManager
import com.epubpro.domain.model.AuthState
import com.epubpro.domain.repository.AuthRepository
import com.epubpro.domain.repository.OnlineNovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel phụ trách cung cấp dữ liệu cấu hình và trạng thái tài khoản cho màn hình Cá nhân.
 *
 * @param serverPreferencesManager Trình quản lý cấu hình kết nối máy chủ.
 * @param onlineNovelRepository Repository kho truyện trực tuyến.
 * @param authRepository Repository quản lý trạng thái xác thực và phiên đăng nhập.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    val serverPreferencesManager: ServerPreferencesManager,
    val onlineNovelRepository: OnlineNovelRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    val baseUrl: StateFlow<String> = serverPreferencesManager.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), serverPreferencesManager.getBaseUrl())

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AuthState.Loading)

    /**
     * Đăng xuất khỏi tài khoản hiện tại và đưa ứng dụng về trạng thái Chưa đăng nhập.
     */
    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }
}
