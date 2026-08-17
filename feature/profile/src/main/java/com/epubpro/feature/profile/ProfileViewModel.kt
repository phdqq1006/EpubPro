package com.epubpro.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.epubpro.core.storage.ServerPreferencesManager
import com.epubpro.domain.repository.OnlineNovelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    val serverPreferencesManager: ServerPreferencesManager,
    val onlineNovelRepository: OnlineNovelRepository
) : ViewModel() {
    val baseUrl: StateFlow<String> = serverPreferencesManager.baseUrlFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), serverPreferencesManager.getBaseUrl())
}
