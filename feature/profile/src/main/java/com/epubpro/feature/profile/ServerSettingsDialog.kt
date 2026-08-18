package com.epubpro.feature.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.epubpro.core.designsystem.R
import com.epubpro.core.storage.ServerPreferencesManager
import com.epubpro.domain.repository.OnlineNovelRepository
import kotlinx.coroutines.launch

@Composable
fun ServerSettingsDialog(
    onDismissRequest: () -> Unit,
    serverPreferencesManager: ServerPreferencesManager,
    onlineNovelRepository: OnlineNovelRepository
) {
    val coroutineScope = rememberCoroutineScope()
    var urlText by remember { mutableStateOf(serverPreferencesManager.getBaseUrl()) }
    var testResult by remember { mutableStateOf<Result<Boolean>?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.server_settings_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        testResult = null
                    },
                    label = { Text(stringResource(R.string.server_url_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Presets
                Text(stringResource(R.string.server_preset_label), style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilterChip(
                        selected = urlText.contains("onrender.com"),
                        onClick = {
                            urlText = ServerPreferencesManager.PRESET_RENDER
                            testResult = null
                        },
                        label = { Text(stringResource(R.string.server_preset_cloud_render), fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = urlText.contains("10.0.2.2"),
                        onClick = {
                            urlText = ServerPreferencesManager.PRESET_EMULATOR
                            testResult = null
                        },
                        label = { Text(stringResource(R.string.server_preset_emulator), fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = urlText.contains("127.0.0.1"),
                        onClick = {
                            urlText = ServerPreferencesManager.PRESET_LOCALHOST
                            testResult = null
                        },
                        label = { Text(stringResource(R.string.server_preset_localhost), fontSize = 11.sp) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Test Connection feedback
                if (isTesting) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.server_checking_status), fontSize = 12.sp)
                    }
                } else if (testResult != null) {
                    if (testResult!!.isSuccess) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.server_test_success), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.server_test_failed), color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResult = null
                            coroutineScope.launch {
                                serverPreferencesManager.saveBaseUrl(urlText)
                                testResult = onlineNovelRepository.testServerConnection()
                                isTesting = false
                            }
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.server_test_connection), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            serverPreferencesManager.saveBaseUrl(urlText)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(stringResource(R.string.server_save), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
