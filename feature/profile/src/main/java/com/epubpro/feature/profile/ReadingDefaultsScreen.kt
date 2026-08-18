package com.epubpro.feature.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignJustify
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.epubpro.core.designsystem.R
import com.epubpro.domain.model.EXTRA_DIM_THRESHOLD
import com.epubpro.domain.model.FontPreset
import com.epubpro.domain.model.ReadingMode
import com.epubpro.domain.model.ReaderSettings
import com.epubpro.domain.model.ReaderThemeMode
import com.epubpro.domain.model.TextAlignment
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingDefaultsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPageTurnControl: () -> Unit = {},
    viewModel: ReadingDefaultsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            ReadingDefaultsTopBar(
                onBack = onNavigateBack,
                onReset = viewModel::resetToDefault,
                onApply = onNavigateBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            // ─── Info card ───────────────────────────────────
            item {
                InfoCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            }

            // ─── Preview box ─────────────────────────────────
            item {
                PreviewTextBox(
                    settings = settings,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ─── Preset chips ─────────────────────────────────
            item {
                PresetsRow(
                    settings = settings,
                    onPresetClick = viewModel::applyPreset,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }

            // ─── Section: Văn bản và hiển thị ─────────────────
            item {
                ExpandableSection(
                    icon = Icons.Default.TextFields,
                    title = "Văn bản và hiển thị",
                    subtitle = buildFontSubtitle(settings),
                    defaultExpanded = true
                ) {
                    TextDisplaySectionContent(
                        settings = settings,
                        onFontSizeChange = viewModel::setFontSize,
                        onLineHeightChange = viewModel::setLineHeight,
                        onFontFamilyChange = viewModel::setFontFamily
                    )
                }
            }

            // ─── Section: Chủ đề ──────────────────────────────
            item {
                ThemeSection(
                    currentTheme = settings.themeMode,
                    brightness = settings.brightness,
                    onThemeSelect = viewModel::setThemeMode,
                    onBrightnessChange = viewModel::setBrightness,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ─── Section: Bố cục ──────────────────────────────
            item {
                ExpandableSection(
                    icon = Icons.Default.FormatLineSpacing,
                    title = "Bố cục",
                    subtitle = if (settings.textAlignment == TextAlignment.LEFT) "Trái" else "Đều hai bên",
                    defaultExpanded = true
                ) {
                    LayoutSectionContent(
                        settings = settings,
                        onMarginHorizontalChange = viewModel::setMarginHorizontal,
                        onMarginVerticalChange = viewModel::setMarginVertical,
                        onParagraphSpacingChange = viewModel::setParagraphSpacing,
                        onFirstLineIndentChange = viewModel::setFirstLineIndent,
                        onTextAlignmentChange = viewModel::setTextAlignment
                    )
                }
            }

            // ─── Section: Chế độ đọc ──────────────────────────
            item {
                ExpandableSection(
                    icon = Icons.Default.ImportContacts,
                    title = "Chế độ đọc",
                    subtitle = settings.readingMode.displayName,
                    defaultExpanded = true
                ) {
                    ReadingModeSectionContent(
                        settings = settings,
                        onReadingModeChange = viewModel::setReadingMode,
                        onShowStatusBarChange = viewModel::setShowStatusBar,
                        onShowScrollBarChange = viewModel::setShowScrollBar,
                        onNavigateToPageTurnControl = onNavigateToPageTurnControl
                    )
                }
            }

            // ─── Section: Hành vi đọc ─────────────────────────
            item {
                ExpandableSection(
                    icon = Icons.Default.Tune,
                    title = "Hành vi đọc",
                    subtitle = "",
                    defaultExpanded = true
                ) {
                    ReadingBehaviorContent(
                        settings = settings,
                        onKeepScreenOnChange = viewModel::setKeepScreenOn
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top Bar
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingDefaultsTopBar(
    onBack: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = "Mặc định đọc",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Trở về"
                )
            }
        },
        actions = {
            IconButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = "Đặt lại mặc định",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onApply,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text("Áp dụng", fontWeight = FontWeight.SemiBold)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Info Card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(8.dp).size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Mặc định đọc",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Áp dụng cho EPUB, Chế độ đọc và chế độ Văn bản PDF.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview Text Box
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PreviewTextBox(settings: ReaderSettings, modifier: Modifier = Modifier) {
    val bgColor = when (settings.themeMode) {
        ReaderThemeMode.DARK, ReaderThemeMode.MIDNIGHT -> Color(0xFF1E1E1E)
        ReaderThemeMode.SEPIA -> Color(0xFFFBF0D9)
        ReaderThemeMode.PAPER -> Color(0xFFF5F0E8)
        else -> Color.White
    }
    val textColor = when (settings.themeMode) {
        ReaderThemeMode.DARK -> Color(0xFFE0E0E0)
        ReaderThemeMode.MIDNIGHT -> Color(0xFFA0A0A0)
        ReaderThemeMode.SEPIA, ReaderThemeMode.PAPER -> Color(0xFF4A3B32)
        else -> Color(0xFF212121)
    }
    val secondaryTextColor = textColor.copy(alpha = 0.6f)

    val padStart = settings.marginLeftDp.coerceIn(4, 36).dp
    val padEnd = settings.marginRightDp.coerceIn(4, 36).dp
    val padTop = settings.marginTopDp.coerceIn(4, 20).dp
    val padBottom = settings.marginBottomDp.coerceIn(4, 20).dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(
                start = padStart,
                end = padEnd,
                top = padTop,
                bottom = padBottom
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "\"But if you have got them today'",
                    fontSize = (settings.fontSizeSp * 0.85f).sp,
                    color = textColor,
                    fontFamily = mapFontFamily(settings.fontFamily),
                    lineHeight = (settings.fontSizeSp * settings.lineHeightRatio * 0.85f).sp,
                    textAlign = if (settings.textAlignment == TextAlignment.JUSTIFY)
                        TextAlign.Justify else TextAlign.Start
                )
                Text(
                    text = "said Elizabeth, \"my mother's",
                    fontSize = (settings.fontSizeSp * 0.85f).sp,
                    color = secondaryTextColor,
                    fontFamily = mapFontFamily(settings.fontFamily),
                    lineHeight = (settings.fontSizeSp * settings.lineHeightRatio * 0.85f).sp
                )
            }
            Icon(
                imageVector = Icons.Default.Fullscreen,
                contentDescription = null,
                tint = textColor.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Presets Row
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PresetsRow(
    settings: ReaderSettings,
    onPresetClick: (ReadingPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    val activePreset = detectActivePreset(settings)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MẪU SẴN",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp
            )

        }

        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReadingPreset.values()) { preset ->
                val isActive = activePreset == preset
                PresetChip(
                    label = preset.label,
                    isActive = isActive,
                    onClick = { onPresetClick(preset) }
                )
            }
        }
    }
}

@Composable
private fun PresetChip(label: String, isActive: Boolean, onClick: () -> Unit) {
    val borderColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        label = "presetBorder"
    )
    val bgColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        label = "presetBg"
    )
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (isActive) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.FormatAlignLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Expandable Section
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExpandableSection(
    icon: ImageVector,
    title: String,
    subtitle: String,
    defaultExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chevron"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = if (expanded) "Thu gọn" else "Mở rộng",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }

            // Content
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    content = content
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section: Văn bản và hiển thị
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TextDisplaySectionContent(
    settings: ReaderSettings,
    onFontSizeChange: (Float) -> Unit,
    onLineHeightChange: (Float) -> Unit,
    onFontFamilyChange: (String) -> Unit
) {
    // Font size slider
    SettingSliderRow(
        icon = Icons.Default.FormatSize,
        label = "Cỡ chữ",
        value = settings.fontSizeSp,
        valueFormatter = { if (it % 1f == 0f) "${it.toInt()}" else "%.1f".format(java.util.Locale.US, it) },
        range = 12f..32f,
        steps = 39,
        onValueChange = { onFontSizeChange(kotlin.math.round(it * 2f) / 2f) }
    )

    Spacer(Modifier.height(4.dp))

    // Line height slider
    SettingSliderRow(
        icon = Icons.Default.FormatLineSpacing,
        label = "Khoảng cách ...",
        value = settings.lineHeightRatio,
        valueFormatter = { "%.2f".format(it) },
        range = 1.0f..3.0f,
        onValueChange = onLineHeightChange
    )

    Spacer(Modifier.height(16.dp))

    // Font family section
    SectionLabel("PHÔNG CHỮ")

    Spacer(Modifier.height(8.dp))

    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FontPreset.values()) { preset ->
            FontPresetChip(
                preset = preset,
                isSelected = settings.fontFamily == preset.fontFamily,
                onClick = { onFontFamilyChange(preset.fontFamily) }
            )
        }
    }

    Spacer(Modifier.height(12.dp))

}

@Composable
private fun FontPresetChip(
    preset: FontPreset,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "fontChipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "fontChipText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
        label = "fontChipBorder"
    )

    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 0.dp else 1.dp,
            color = borderColor
        )
    ) {
        Text(
            text = preset.displayName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            fontFamily = mapFontFamily(preset.fontFamily)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section: Chủ đề
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ThemeSection(
    currentTheme: ReaderThemeMode,
    brightness: Float,
    onThemeSelect: (ReaderThemeMode) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionLabel("CHỦ ĐỀ")
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeCell(
                    label = "Sáng",
                    bgColor = Color.White,
                    textColor = Color(0xFF212121),
                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    isSelected = currentTheme == ReaderThemeMode.LIGHT,
                    onClick = { onThemeSelect(ReaderThemeMode.LIGHT) },
                    modifier = Modifier.weight(1f)
                )
                ThemeCell(
                    label = "Tối",
                    bgColor = Color(0xFF1E1E1E),
                    textColor = Color(0xFFE0E0E0),
                    borderColor = Color.Transparent,
                    isSelected = currentTheme == ReaderThemeMode.DARK,
                    onClick = { onThemeSelect(ReaderThemeMode.DARK) },
                    modifier = Modifier.weight(1f)
                )
                ThemeCell(
                    label = "Ấm",
                    bgColor = Color(0xFFFBF0D9),
                    textColor = Color(0xFF4A3B32),
                    borderColor = Color.Transparent,
                    isSelected = currentTheme == ReaderThemeMode.SEPIA,
                    onClick = { onThemeSelect(ReaderThemeMode.SEPIA) },
                    modifier = Modifier.weight(1f)
                )
                ThemeCell(
                    label = "Giấy",
                    bgColor = Color(0xFFF5F0E8),
                    textColor = Color(0xFF3C3530),
                    borderColor = MaterialTheme.colorScheme.primary,
                    isSelected = currentTheme == ReaderThemeMode.PAPER,
                    onClick = { onThemeSelect(ReaderThemeMode.PAPER) },
                    modifier = Modifier.weight(1f)
                )
                ThemeCell(
                    label = "Đêm",
                    bgColor = Color(0xFF0D1117),
                    textColor = Color(0xFF8B949E),
                    borderColor = Color.Transparent,
                    isSelected = currentTheme == ReaderThemeMode.MIDNIGHT,
                    onClick = { onThemeSelect(ReaderThemeMode.MIDNIGHT) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(16.dp))

            val brightnessPercent = (brightness * 100).toInt()
            val isExtraDim = brightness < EXTRA_DIM_THRESHOLD
            val brightnessLabel = if (isExtraDim) {
                stringResource(R.string.reader_brightness_extra_dim_format, brightnessPercent)
            } else {
                stringResource(R.string.reader_brightness_percent_format, brightnessPercent)
            }
            val hudFormat = stringResource(R.string.reader_brightness_hud_format)
            SectionLabel(stringResource(R.string.reading_defaults_brightness_section))
            Spacer(Modifier.height(8.dp))

            SettingSliderRow(
                icon = if (isExtraDim) Icons.Default.Nightlight else Icons.Default.BrightnessMedium,
                label = brightnessLabel,
                value = brightness,
                valueFormatter = { value -> hudFormat.format((value * 100).toInt()) },
                range = 0.0f..1.0f,
                onValueChange = onBrightnessChange
            )
        }
    }
}

@Composable
private fun ThemeCell(
    label: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else borderColor,
        label = "themeBorder"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = animatedBorderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Aa",
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 11.sp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section: Bố cục
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LayoutSectionContent(
    settings: ReaderSettings,
    onMarginHorizontalChange: (Int) -> Unit,
    onMarginVerticalChange: (Int) -> Unit,
    onParagraphSpacingChange: (Int) -> Unit,
    onFirstLineIndentChange: (Int) -> Unit,
    onTextAlignmentChange: (TextAlignment) -> Unit
) {
    SectionLabel("LỀ TRANG")
    Spacer(Modifier.height(8.dp))

    // Margin horizontal
    SettingSliderRow(
        icon = Icons.Default.SwapHoriz,
        label = "Lề ngang",
        value = settings.marginLeftDp.toFloat(),
        valueFormatter = { it.roundToInt().toString() },
        range = 0f..64f,
        steps = 63,
        onValueChange = { onMarginHorizontalChange(it.roundToInt()) }
    )
    Spacer(Modifier.height(4.dp))

    // Margin vertical
    SettingSliderRow(
        icon = Icons.Default.SwapVert,
        label = "Lề trên dưới",
        value = settings.marginTopDp.toFloat(),
        valueFormatter = { it.roundToInt().toString() },
        range = 0f..64f,
        steps = 63,
        onValueChange = { onMarginVerticalChange(it.roundToInt()) }
    )

    Spacer(Modifier.height(16.dp))
    SectionLabel("ĐOẠN VĂN")
    Spacer(Modifier.height(8.dp))

    // Paragraph spacing
    SettingSliderRow(
        icon = Icons.Default.FormatLineSpacing,
        label = "Khoảng cách đ...",
        value = settings.paragraphSpacingDp.toFloat(),
        valueFormatter = { it.roundToInt().toString() },
        range = 0f..32f,
        steps = 31,
        onValueChange = { onParagraphSpacingChange(it.roundToInt()) }
    )
    Spacer(Modifier.height(4.dp))

    // First line indent
    SettingSliderRow(
        icon = Icons.Default.FormatIndentIncrease,
        label = "Thụt đầu dòng",
        value = settings.firstLineIndentDp.toFloat(),
        valueFormatter = { it.roundToInt().toString() },
        range = 0f..32f,
        steps = 31,
        onValueChange = { onFirstLineIndentChange(it.roundToInt()) }
    )

    Spacer(Modifier.height(16.dp))

    // Text alignment buttons
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AlignmentButton(
            label = "Trái",
            icon = Icons.AutoMirrored.Filled.FormatAlignLeft,
            isSelected = settings.textAlignment == TextAlignment.LEFT,
            onClick = { onTextAlignmentChange(TextAlignment.LEFT) },
            modifier = Modifier.weight(1f)
        )
        AlignmentButton(
            label = "Đều hai bên",
            icon = Icons.Default.FormatAlignJustify,
            isSelected = settings.textAlignment == TextAlignment.JUSTIFY,
            onClick = { onTextAlignmentChange(TextAlignment.JUSTIFY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AlignmentButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "alignBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "alignBorder"
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section: Chế độ đọc
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadingModeSectionContent(
    settings: ReaderSettings,
    onReadingModeChange: (ReadingMode) -> Unit,
    onShowStatusBarChange: (Boolean) -> Unit,
    onShowScrollBarChange: (Boolean) -> Unit,
    onNavigateToPageTurnControl: () -> Unit = {}
) {
    val modes = listOf(
        Triple(ReadingMode.SCROLL, Icons.Default.Article, "Cuộn dọc"),
        Triple(ReadingMode.FLIP, Icons.Default.MenuBook, "Lật trang")
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        modes.forEach { (mode, icon, label) ->
            ReadingModeCard(
                label = label,
                icon = icon,
                isSelected = settings.readingMode == mode,
                onClick = { onReadingModeChange(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    Spacer(Modifier.height(12.dp))

    // Toggles
    ToggleRow(
        icon = Icons.Default.ViewHeadline,
        label = "Hiển thị thanh trạng thái\nphía dưới",
        checked = settings.showStatusBar,
        onCheckedChange = onShowStatusBarChange
    )

    ToggleRow(
        icon = Icons.Default.LinearScale,
        label = "Hiển thị thanh cuộn",
        checked = settings.showScrollBar,
        onCheckedChange = onShowScrollBarChange
    )

    // Navigate row
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onNavigateToPageTurnControl() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(7.dp).size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Điều khiển chuyển trang",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    settings.tapZoneLayout.displayName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ReadingModeCard(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "readingModeBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "readingModeBorder"
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 0.dp,
            color = borderColor
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp
            )
            if (isSelected) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Section: Hành vi đọc
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReadingBehaviorContent(
    settings: ReaderSettings,
    onKeepScreenOnChange: (Boolean) -> Unit
) {
    ToggleRow(
        icon = Icons.Default.LightMode,
        label = "Giữ màn hình luôn sáng",
        checked = settings.keepScreenOn,
        onCheckedChange = onKeepScreenOnChange
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared UI Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SettingSliderRow(
    icon: ImageVector,
    label: String,
    value: Float,
    valueFormatter: (Float) -> String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    var draftValue by remember(value) { mutableFloatStateOf(value) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp)
        )
        Slider(
            value = draftValue,
            onValueChange = { draftValue = it },
            onValueChangeFinished = { onValueChange(draftValue) },
            valueRange = range,
            steps = steps,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            )
        )
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                text = valueFormatter(draftValue),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(7.dp).size(16.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )
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
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun mapFontFamily(fontFamily: String): FontFamily = when (fontFamily.lowercase()) {
    "serif" -> FontFamily.Serif
    "sans-serif" -> FontFamily.SansSerif
    "monospace" -> FontFamily.Monospace
    else -> FontFamily.Serif
}

private fun buildFontSubtitle(settings: ReaderSettings): String {
    val preset = FontPreset.values().firstOrNull { it.fontFamily == settings.fontFamily }
    val fontName = preset?.displayName ?: settings.fontFamily
    val formattedFontSize = if (settings.fontSizeSp % 1f == 0f) {
        "${settings.fontSizeSp.toInt()}"
    } else {
        "%.1f".format(java.util.Locale.US, settings.fontSizeSp)
    }
    return "$fontName · $formattedFontSize"
}

private fun detectActivePreset(settings: ReaderSettings): ReadingPreset? {
    return when {
        settings.fontSizeSp == 18f && settings.lineHeightRatio == 1.5f && settings.marginLeftDp == 16 -> ReadingPreset.DEFAULT
        settings.fontSizeSp == 16f && settings.lineHeightRatio == 1.3f && settings.marginLeftDp == 12 -> ReadingPreset.COMPACT
        settings.fontSizeSp == 20f && settings.lineHeightRatio == 1.8f && settings.marginLeftDp == 24 -> ReadingPreset.COMFORTABLE
        settings.fontSizeSp == 17f && settings.lineHeightRatio == 1.6f && settings.marginLeftDp == 20 -> ReadingPreset.READER
        else -> null
    }
}

private val ReadingMode.displayName: String
    get() = when (this) {
        ReadingMode.SCROLL -> "Cuộn"
        ReadingMode.SCROLL_HORIZONTAL -> "Cuộn ngang"
        ReadingMode.FLIP -> "Lật"
        ReadingMode.CONTINUOUS -> "Cuốn"
    }
