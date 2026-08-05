package com.epubpro.feature.profile

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.MAX_PAGE_TURN_SPEED_MS
import com.epubpro.domain.model.MIN_PAGE_TURN_SPEED_MS
import com.epubpro.domain.model.PAGE_TURN_SPEED_PRESETS_MS
import com.epubpro.domain.model.TapZoneAction
import com.epubpro.domain.model.TapZoneLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageTurnControlScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReadingDefaultsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    var showCustomTapZoneSheet by remember { mutableStateOf(false) }
    var draftPageTurnSpeedMs by remember(settings.pageTurnSpeedMs) { mutableIntStateOf(settings.pageTurnSpeedMs) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Điều khiển chuyển trang",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Trở về"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ─── Info Banner ──────────────────────────────────
            item {
                InfoBannerCard()
            }

            // ─── Section Title: Chạm chuyển trang ─────────────
            item {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = "Chạm chuyển trang",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = settings.tapZoneLayout.displayName,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── 3 Tap Zone Preset Cards ──────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TapZonePresetCard(
                        layout = TapZoneLayout.HORIZONTAL,
                        isSelected = settings.tapZoneLayout == TapZoneLayout.HORIZONTAL,
                        onClick = { viewModel.setTapZoneLayout(TapZoneLayout.HORIZONTAL) },
                        modifier = Modifier.weight(1f)
                    )
                    TapZonePresetCard(
                        layout = TapZoneLayout.VERTICAL,
                        isSelected = settings.tapZoneLayout == TapZoneLayout.VERTICAL,
                        onClick = { viewModel.setTapZoneLayout(TapZoneLayout.VERTICAL) },
                        modifier = Modifier.weight(1f)
                    )
                    TapZonePresetCard(
                        layout = TapZoneLayout.BOTTOM_SPLIT,
                        isSelected = settings.tapZoneLayout == TapZoneLayout.BOTTOM_SPLIT,
                        onClick = { viewModel.setTapZoneLayout(TapZoneLayout.BOTTOM_SPLIT) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ─── Custom Tap Zone Card ─────────────────────────
            item {
                CustomTapZoneCard(
                    onClick = { showCustomTapZoneSheet = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // ─── Toggle Switches ──────────────────────────────
            item {
                PageTurnToggleRow(
                    icon = Icons.Default.FilterNone,
                    title = "Hiệu ứng chuyển trang",
                    checked = settings.enablePageAnimation,
                    onCheckedChange = { viewModel.setPageAnimation(it) }
                )
            }

            item {
                PageTurnSpeedControl(
                    speedMs = draftPageTurnSpeedMs,
                    onSpeedChanged = { draftPageTurnSpeedMs = it },
                    onSpeedChangeFinished = { viewModel.setPageTurnSpeed(draftPageTurnSpeedMs) },
                    onPresetSelected = { speed ->
                        draftPageTurnSpeedMs = speed
                        viewModel.setPageTurnSpeed(speed)
                    }
                )
            }

            item {
                PageTurnToggleRow(
                    icon = Icons.Default.Keyboard,
                    title = "Chuyển trang bằng bàn phím",
                    subtitle = "Phím mũi tên, Page Up/Down và phím cách",
                    checked = settings.enableKeyboardNavigation,
                    onCheckedChange = { viewModel.setKeyboardNavigation(it) }
                )
            }

            item {
                PageTurnToggleRow(
                    icon = Icons.Default.VolumeUp,
                    title = "Lật trang bằng nút âm lượng",
                    checked = settings.enableVolumeKeyNavigation,
                    onCheckedChange = { viewModel.setVolumeKeyNavigation(it) }
                )
            }
        }
    }

    if (showCustomTapZoneSheet) {
        CustomTapZoneBottomSheet(
            currentActions = settings.tapZoneActions,
            onSave = viewModel::setTapZoneActions,
            onDismiss = { showCustomTapZoneSheet = false }
        )
    }
}

@Composable
private fun PageTurnSpeedControl(
    speedMs: Int,
    onSpeedChanged: (Int) -> Unit,
    onSpeedChangeFinished: () -> Unit,
    onPresetSelected: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Tốc độ lật trang: $speedMs ms",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Slider(
            value = speedMs.toFloat(),
            onValueChange = { onSpeedChanged(it.toInt()) },
            onValueChangeFinished = onSpeedChangeFinished,
            valueRange = MIN_PAGE_TURN_SPEED_MS.toFloat()..MAX_PAGE_TURN_SPEED_MS.toFloat()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PAGE_TURN_SPEED_PRESETS_MS.zip(listOf("Nhanh", "Vừa", "Chậm")).forEach { (speed, name) ->
                FilterChip(
                    selected = speedMs == speed,
                    onClick = { onPresetSelected(speed) },
                    label = { Text("$name (${speed}ms)", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Banner
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoBannerCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Thay đổi được lưu tự động và đồng bộ với màn đọc",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 18.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Tap Zone Preset Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TapZonePresetCard(
    layout: TapZoneLayout,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFE5DDD3),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else Color(0xFFF9F6F0),
        label = "bgColor"
    )

    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Diagram representation inside card
        TapZoneDiagram(
            layout = layout,
            isSelected = isSelected,
            modifier = Modifier.fillMaxSize()
        )

        // Selected checkmark indicator badge top-right
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

@Composable
private fun TapZoneDiagram(
    layout: TapZoneLayout,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color(0xFFB0A8A0)
    val iconColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF6B635B)

    Box(modifier = modifier.padding(10.dp), contentAlignment = Alignment.Center) {
        // Dotted layout lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 2.dp.toPx()
            val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

            when (layout) {
                TapZoneLayout.HORIZONTAL -> {
                    // Two vertical dotted lines dividing into 3 columns
                    val colWidth = size.width / 3f
                    // Left line
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, 0f),
                        end = androidx.compose.ui.geometry.Offset(colWidth, size.height),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    // Right line
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth * 2, 0f),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, size.height),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    // Middle horizontal box dotted lines
                    val rowHeight = size.height / 3f
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, rowHeight),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, rowHeight),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, rowHeight * 2),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, rowHeight * 2),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                }
                TapZoneLayout.VERTICAL -> {
                    // Two horizontal dotted lines dividing into 3 rows
                    val rowHeight = size.height / 3f
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, rowHeight),
                        end = androidx.compose.ui.geometry.Offset(size.width, rowHeight),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, rowHeight * 2),
                        end = androidx.compose.ui.geometry.Offset(size.width, rowHeight * 2),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    // Middle vertical box dotted lines
                    val colWidth = size.width / 3f
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, rowHeight),
                        end = androidx.compose.ui.geometry.Offset(colWidth, rowHeight * 2),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth * 2, rowHeight),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, rowHeight * 2),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                }
                TapZoneLayout.BOTTOM_SPLIT -> {
                    // Upper section & Lower section horizontal divider line
                    val rowHeight = size.height * 0.55f
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, rowHeight),
                        end = androidx.compose.ui.geometry.Offset(size.width, rowHeight),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    // Lower section vertical split line
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(size.width / 2f, rowHeight),
                        end = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    // Middle box for menu in upper section
                    val colWidth = size.width / 3f
                    val topBoxHeight = rowHeight * 0.7f
                    val topBoxY = rowHeight * 0.15f
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, topBoxY),
                        end = androidx.compose.ui.geometry.Offset(colWidth, topBoxY + topBoxHeight),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth * 2, topBoxY),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, topBoxY + topBoxHeight),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, topBoxY),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, topBoxY),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                    drawLine(
                        color = lineColor,
                        start = androidx.compose.ui.geometry.Offset(colWidth, topBoxY + topBoxHeight),
                        end = androidx.compose.ui.geometry.Offset(colWidth * 2, topBoxY + topBoxHeight),
                        strokeWidth = strokeWidth,
                        pathEffect = dashPathEffect
                    )
                }
            }
        }

        // Icons overlaid on the diagram
        when (layout) {
            TapZoneLayout.HORIZONTAL -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                }
            }
            TapZoneLayout.VERTICAL -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                    }
                }
            }
            TapZoneLayout.BOTTOM_SPLIT -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(0.55f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Menu, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
                    }
                    Row(modifier = Modifier.weight(0.45f).fillMaxWidth()) {
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                        }
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Custom Tap Zone Card (Tùy chỉnh vùng chạm)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CustomTapZoneCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mini 3x3 grid illustration container
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFF3ECE6))
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    repeat(3) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("←", fontSize = 9.sp, color = Color(0xFF70675E))
                            Text("👁", fontSize = 8.sp, color = Color(0xFF70675E))
                            Text("→", fontSize = 9.sp, color = Color(0xFF70675E))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tùy chỉnh vùng chạm",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Gán hành động riêng cho 9 vùng màn hình",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Page Turn Toggle Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PageTurnToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
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

// ─────────────────────────────────────────────────────────────────────────────
// Custom 9-Zone Tap Bottom Sheet / Dialog
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomTapZoneBottomSheet(
    currentActions: List<TapZoneAction>,
    onSave: (List<TapZoneAction>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedZoneIndex by remember { mutableStateOf<Int?>(null) }
    val zoneActions = remember(currentActions) {
        mutableStateMapOf<Int, TapZoneAction>().apply {
            currentActions.forEachIndexed { index, action -> put(index, action) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Tùy chỉnh 9 vùng màn hình",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Chạm vào một vùng bên dưới để chọn hành động tương ứng",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3x3 Grid Matrix
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF5EFE6))
                    .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    repeat(3) { rowIndex ->
                        Row(modifier = Modifier.weight(1f)) {
                            repeat(3) { colIndex ->
                                val zoneIndex = rowIndex * 3 + colIndex
                                val action = zoneActions[zoneIndex] ?: TapZoneAction.TOGGLE_CONTROLS
                                val isSelected = selectedZoneIndex == zoneIndex

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(0.5.dp, Color(0xFFD0C5B8))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                                        )
                                        .clickable { selectedZoneIndex = zoneIndex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = getActionIconText(action),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = action.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Selected Zone Action Selector Dialog
            if (selectedZoneIndex != null) {
                Text(
                    text = "Hành động cho Vùng ${selectedZoneIndex!! + 1}:",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TapZoneAction.values().forEach { action ->
                        FilterChip(
                            selected = zoneActions[selectedZoneIndex] == action,
                            onClick = {
                                zoneActions[selectedZoneIndex!!] = action
                            },
                            label = { Text(action.label, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSave(List(9) { index -> zoneActions[index] ?: TapZoneAction.TOGGLE_CONTROLS })
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Hoàn tất", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun getActionIconText(action: TapZoneAction): String = when (action) {
    TapZoneAction.PREV_PAGE -> "←"
    TapZoneAction.NEXT_PAGE -> "→"
    TapZoneAction.TOGGLE_CONTROLS -> "Menu"
}