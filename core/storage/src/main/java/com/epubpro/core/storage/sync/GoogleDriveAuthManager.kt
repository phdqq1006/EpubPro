package com.epubpro.core.storage.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cầu nối lấy OAuth access token ngắn hạn cho Google Drive.
 *
 * Token chỉ tồn tại trong bộ nhớ của phiên gọi; không ghi vào DataStore, log hay manifest.
 */
@Singleton
class GoogleDriveAuthManager @Inject constructor(
    @ApplicationContext context: Context
) {
    private val accountManager = AccountManager.get(context)

    /**
     * Lấy access token cho tài khoản Google đầu tiên trên thiết bị.
     *
     * @return Token trong bộ nhớ, hoặc null nếu cần người dùng cấp quyền/đăng nhập.
     */
    suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        val account = accountManager.getAccountsByType(GOOGLE_ACCOUNT_TYPE).firstOrNull() ?: return@withContext null
        val result = accountManager.getAuthToken(account, TOKEN_TYPE, null, false, null, null).result
        result.getString(AccountManager.KEY_AUTHTOKEN)
    }

    /**
     * Loại bỏ token bị từ chối để lần sau Android xin token mới.
     *
     * @param token Token đã bị Drive từ chối.
     */
    fun invalidate(token: String) {
        accountManager.invalidateAuthToken(GOOGLE_ACCOUNT_TYPE, token)
    }

    companion object {
        /** Scope OAuth tối thiểu để app chỉ thao tác file do app tạo/chọn trên Drive. */
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val TOKEN_TYPE = "oauth2:$DRIVE_FILE_SCOPE"
    }
}

/** File remote cùng key để download/delete mà không tin cache ID tuyệt đối. */
data class DriveFileRef(
    val id: String,
    val key: String,
    val parentId: String
)

/** Snapshot remote đọc từ manifest và cây folder Drive. */
data class DriveSyncSnapshot(
    val manifest: com.epubpro.domain.sync.SyncManifest,
    val files: Map<String, DriveFileRef>
)

/** Lỗi đã được phân loại ở boundary Drive. */
class DriveSyncException(
    message: String,
    val requiresAuth: Boolean = false,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
