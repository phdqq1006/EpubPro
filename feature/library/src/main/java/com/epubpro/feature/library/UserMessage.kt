package com.epubpro.feature.library

import androidx.annotation.StringRes

/**
 * Thông điệp một lần gửi từ ViewModel tới giao diện dưới dạng String Resource.
 *
 * @property textRes Resource chứa nội dung thông báo cần hiển thị.
 * @property formatArgs Danh sách tham số dùng để định dạng resource.
 */
data class UserMessage(
    @StringRes val textRes: Int,
    val formatArgs: List<Any> = emptyList()
)
