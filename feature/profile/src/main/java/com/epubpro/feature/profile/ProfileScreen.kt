package com.epubpro.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.AuthState
import com.epubpro.domain.model.User
import java.util.Locale

/**
 * Màn hình Cá nhân (Profile) hiển thị thông tin tài khoản người dùng, đồng bộ dữ liệu và cài đặt ứng dụng.
 *
 * @param onNavigateToLogin Callback mở màn hình Đăng nhập / Đăng ký.
 * @param onNavigateToAudioSettings Callback mở cài đặt âm thanh TTS.
 * @param onNavigateToReadingDefaults Callback mở cài đặt mặc định đọc.
 * @param onNavigateToContentFilter Callback mở màn hình lọc nội dung.
 * @param viewModel ViewModel quản lý dữ liệu cấu hình và trạng thái tài khoản.
 */
@Composable
fun ProfileScreen(
    onNavigateToLogin: () -> Unit = {},
    onNavigateToAudioSettings: () -> Unit = {},
    onNavigateToReadingDefaults: () -> Unit = {},
    onNavigateToContentFilter: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel()
) {
    var showServerSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showAccountDetailDialog by rememberSaveable { mutableStateOf(false) }

    val authState by viewModel.authState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.profile_title),
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp)
                    .semantics { heading() },
                textAlign = TextAlign.Center
            )
        }

        // Header Card thông tin tài khoản / Đăng nhập
        item {
            ProfileUserHeaderCard(
                authState = authState,
                onLoginClick = onNavigateToLogin,
                onLogoutClick = { showLogoutConfirmDialog = true },
                onAccountClick = { showAccountDetailDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            ProfileSectionHeader(
                title = stringResource(R.string.profile_section_sync),
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_drive_sync_title),
                subtitle = stringResource(R.string.profile_drive_sync_subtitle),
                icon = Icons.Default.CloudSync,
                onClick = { },
                enabled = false
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            ProfileSectionHeader(
                title = stringResource(R.string.profile_section_settings),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_nav_custom_title),
                subtitle = stringResource(R.string.profile_nav_custom_subtitle),
                icon = Icons.Default.ViewCarousel,
                onClick = { },
                enabled = false
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_appearance_title),
                subtitle = stringResource(R.string.profile_appearance_subtitle),
                icon = Icons.Default.Palette,
                onClick = { },
                enabled = false
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_reader_defaults_title),
                subtitle = stringResource(R.string.profile_reader_defaults_subtitle),
                icon = Icons.AutoMirrored.Filled.MenuBook,
                onClick = onNavigateToReadingDefaults
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_audio_settings_title),
                subtitle = stringResource(R.string.profile_audio_settings_subtitle),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                onClick = onNavigateToAudioSettings
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_highlight_tags_title),
                subtitle = stringResource(R.string.profile_highlight_tags_subtitle),
                icon = Icons.AutoMirrored.Filled.Label,
                onClick = { },
                enabled = false
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            ProfileSectionHeader(
                title = stringResource(R.string.profile_section_advanced),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.profile_content_filter_title),
                subtitle = stringResource(R.string.profile_content_filter_subtitle),
                icon = Icons.Default.FilterAlt,
                onClick = onNavigateToContentFilter
            )
        }

        item {
            ProfileItem(
                title = stringResource(R.string.server_settings_title),
                subtitle = stringResource(R.string.server_settings_subtitle),
                icon = Icons.Default.Dns,
                onClick = { showServerSettingsDialog = true }
            )
        }

        // Mục Tài khoản khi đã đăng nhập
        if (authState is AuthState.Authenticated) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ProfileSectionHeader(
                    title = stringResource(R.string.auth_profile_section_account),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            item {
                ProfileItem(
                    title = stringResource(R.string.auth_profile_edit_account_title),
                    subtitle = stringResource(R.string.auth_profile_edit_account_subtitle),
                    icon = Icons.Default.Badge,
                    onClick = { showAccountDetailDialog = true }
                )
            }

            item {
                ProfileItem(
                    title = stringResource(R.string.auth_profile_logout_title),
                    subtitle = stringResource(R.string.auth_profile_logout_subtitle),
                    icon = Icons.AutoMirrored.Filled.Logout,
                    onClick = { showLogoutConfirmDialog = true }
                )
            }
        }
    }

    if (showServerSettingsDialog) {
        ServerSettingsDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            serverPreferencesManager = viewModel.serverPreferencesManager,
            onlineNovelRepository = viewModel.onlineNovelRepository
        )
    }

    if (showAccountDetailDialog) {
        val currentUser = (authState as? AuthState.Authenticated)?.user
        if (currentUser != null) {
            AccountDetailDialog(
                user = currentUser,
                onDismiss = { showAccountDetailDialog = false }
            )
        }
    }

    if (showLogoutConfirmDialog) {
        val currentUser = (authState as? AuthState.Authenticated)?.user
        LogoutConfirmDialog(
            userName = currentUser?.displayName ?: "",
            onDismiss = { showLogoutConfirmDialog = false },
            onConfirm = {
                showLogoutConfirmDialog = false
                viewModel.logout()
            }
        )
    }
}

/**
 * Thẻ Card hiển thị trạng thái hồ sơ người dùng hoặc nút đăng nhập ở đầu màn hình Cá nhân.
 *
 * @param authState Trạng thái xác thực hiện tại.
 * @param onLoginClick Callback khi người dùng nhấn nút Đăng nhập / Đăng ký.
 * @param onLogoutClick Callback khi người dùng nhấn nút Đăng xuất.
 * @param onAccountClick Callback khi người dùng nhấn xem chi tiết tài khoản.
 * @param modifier Modifier tùy biến bố cục.
 */
@Composable
fun ProfileUserHeaderCard(
    authState: AuthState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit = {},
    onAccountClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        when (authState) {
            is AuthState.Authenticated -> {
                AuthenticatedUserContent(
                    user = authState.user,
                    onLogoutClick = onLogoutClick,
                    onAccountClick = onAccountClick
                )
            }
            else -> {
                UnauthenticatedUserContent(onLoginClick = onLoginClick)
            }
        }
    }
}

/**
 * Nội dung hiển thị trong thẻ khi người dùng đã đăng nhập tài khoản thành công.
 *
 * @param user Đối tượng người dùng chứa thông tin tài khoản và số liệu đọc sách.
 * @param onLogoutClick Callback đăng xuất tài khoản.
 * @param onAccountClick Callback mở hộp thoại thông tin tài khoản.
 */
@Composable
private fun AuthenticatedUserContent(
    user: User,
    onLogoutClick: () -> Unit = {},
    onAccountClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Avatar hình tròn
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAccountClick),
                contentAlignment = Alignment.Center
            ) {
                val initial = user.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"
                Text(
                    text = initial,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onAccountClick)
            ) {
                Text(
                    text = user.displayName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = user.email,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = user.membershipTier,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Nút Đăng xuất nhanh ngay trên Card hồ sơ
            IconButton(
                onClick = onLogoutClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = stringResource(R.string.auth_profile_logout_title),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(14.dp))

        // Thống kê nhanh mini
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ProfileStatItem(
                value = "${user.totalReadBooks}",
                label = stringResource(R.string.auth_profile_stat_books)
            )
            ProfileStatItem(
                value = "${user.readingStreakDays}",
                label = stringResource(R.string.auth_profile_stat_streak)
            )
            ProfileStatItem(
                value = String.format(Locale.getDefault(), "%.1f", user.totalReadHours),
                label = stringResource(R.string.auth_profile_stat_hours)
            )
        }
    }
}

/**
 * Một cột hiển thị chỉ số thống kê cá nhân (giá trị + nhãn mô tả).
 *
 * @param value Giá trị số liệu.
 * @param label Nhãn mô tả số liệu.
 */
@Composable
private fun ProfileStatItem(
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Nội dung hiển thị trong thẻ khi chưa đăng nhập, kèm nút mời gọi đăng nhập.
 *
 * @param onLoginClick Callback chuyển đến màn hình Đăng nhập.
 */
@Composable
private fun UnauthenticatedUserContent(
    onLoginClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.auth_profile_guest_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.auth_profile_guest_subtitle),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onLoginClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Login,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.auth_profile_btn_login),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Hộp thoại xác nhận đăng xuất khỏi tài khoản.
 *
 * @param userName Tên người dùng đang đăng nhập.
 * @param onDismiss Callback khi người dùng hủy.
 * @param onConfirm Callback khi người dùng xác nhận đăng xuất.
 */
@Composable
private fun LogoutConfirmDialog(
    userName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.auth_logout_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.auth_logout_confirm_msg, userName),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = stringResource(R.string.auth_btn_logout),
                    color = MaterialTheme.colorScheme.onError
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ProfileSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics { heading() }
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        )
    }
}

@Composable
fun ProfileItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onBackground
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }
    val iconColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (!enabled) disabled()
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (enabled) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = stringResource(R.string.profile_coming_soon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProfileItemWithSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * Hộp thoại hiển thị chi tiết hồ sơ tài khoản người dùng và trạng thái phiên đăng nhập.
 *
 * @param user Thông tin người dùng hiện tại.
 * @param onDismiss Callback đóng hộp thoại.
 */
@Composable
private fun AccountDetailDialog(
    user: User,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.auth_profile_edit_account_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                AccountInfoRow(
                    label = stringResource(R.string.auth_account_detail_name),
                    value = user.displayName
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccountInfoRow(
                    label = stringResource(R.string.auth_account_detail_email),
                    value = user.email
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccountInfoRow(
                    label = stringResource(R.string.auth_account_detail_id),
                    value = user.id.take(16) + "..."
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccountInfoRow(
                    label = stringResource(R.string.auth_account_detail_provider),
                    value = user.provider.name
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccountInfoRow(
                    label = stringResource(R.string.auth_account_detail_tier),
                    value = user.membershipTier
                )
                Spacer(modifier = Modifier.height(10.dp))
                AccountInfoRow(
                    label = stringResource(R.string.auth_account_detail_token_status),
                    value = stringResource(R.string.auth_account_detail_token_valid)
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

/**
 * Một hàng hiển thị nhãn và giá trị thông tin trong hộp thoại tài khoản.
 *
 * @param label Tên trường thông tin.
 * @param value Giá trị hiển thị.
 */
@Composable
private fun AccountInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
