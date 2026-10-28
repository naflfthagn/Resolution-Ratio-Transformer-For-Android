@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.resizelab.app.ui

import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.resizelab.app.R
import com.resizelab.app.data.AudioQuality
import com.resizelab.app.data.ExportResult
import com.resizelab.app.data.ExportStage
import com.resizelab.app.data.ImageFormat
import com.resizelab.app.data.MediaInfo
import com.resizelab.app.data.MediaKind
import com.resizelab.app.data.ResolutionPreset
import com.resizelab.app.data.ResizeMode
import com.resizelab.app.media.ImagePreviewLoader
import com.resizelab.app.ui.components.AppCard
import com.resizelab.app.ui.components.AppCardShape
import com.resizelab.app.ui.components.AppCheckSquare
import com.resizelab.app.ui.components.AppChoice
import com.resizelab.app.ui.components.AppControlShape
import com.resizelab.app.ui.components.AppSquareIconButton
import com.resizelab.app.ui.components.ExpandableHeader
import com.resizelab.app.ui.components.PrimaryActionButton
import com.resizelab.app.ui.components.SecondaryActionButton
import com.resizelab.app.util.ResolutionMath
import com.resizelab.app.util.formatBytes
import com.resizelab.app.util.formatDuration
import com.resizelab.app.util.formatTypeLabel
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    state: EditorUiState,
    onBack: () -> Unit,
    onPickNew: () -> Unit,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onPresetSelected: (ResolutionPreset) -> Unit,
    onToggleAspectLock: () -> Unit,
    onModeSelected: (ResizeMode) -> Unit,
    onFormatSelected: (ImageFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
    onAudioQualitySelected: (AudioQuality) -> Unit,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onOpenResult: (ExportResult) -> Unit,
    onShareResult: (ExportResult) -> Unit,
    onMessage: (String) -> Unit
) {
    val media = state.media ?: return
    val colors = MaterialTheme.colorScheme
    val exportingMessage = stringResource(R.string.msg_export_in_progress)
    var settingsExpanded by rememberSaveable { mutableStateOf(false) }

    val handleBack = {
        if (state.isExporting) onMessage(exportingMessage) else onBack()
    }
    BackHandler { handleBack() }

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.editor_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    Box(Modifier.padding(start = 12.dp)) {
                        AppSquareIconButton(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                            onClick = handleBack
                        )
                    }
                },
                actions = {
                    SecondaryActionButton(
                        label = stringResource(R.string.action_change),
                        onClick = onPickNew,
                        enabled = !state.isExporting,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.background,
                    titleContentColor = colors.onBackground,
                    navigationIconContentColor = colors.onBackground,
                    actionIconContentColor = colors.onSurface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PreviewSection(media = media, isExporting = state.isExporting)
            }
            item {
                ResolutionCard(
                    state = state,
                    onWidthChange = onWidthChange,
                    onHeightChange = onHeightChange,
                    onPresetSelected = onPresetSelected,
                    onToggleAspectLock = onToggleAspectLock,
                    onModeSelected = onModeSelected
                )
            }
            item {
                ExportSettingsCard(
                    state = state,
                    expanded = settingsExpanded,
                    onToggle = { settingsExpanded = !settingsExpanded },
                    onFormatSelected = onFormatSelected,
                    onQualityChange = onQualityChange,
                    onAudioQualitySelected = onAudioQualitySelected
                )
            }
            item {
                ExportSection(
                    state = state,
                    media = media,
                    targetValid = isTargetValid(state),
                    onExport = onExport,
                    onCancelExport = onCancelExport,
                    onOpenResult = onOpenResult,
                    onShareResult = onShareResult
                )
            }
        }
    }
}

private fun isTargetValid(state: EditorUiState): Boolean {
    val target = state.targetSize ?: return false
    return ResolutionMath.validateTarget(target, state.isVideo) == null
}

@Composable
private fun PreviewSection(media: MediaInfo, isExporting: Boolean) {
    val colors = MaterialTheme.colorScheme
    val typeLabel = stringResource(
        if (media.kind == MediaKind.IMAGE) R.string.source_image else R.string.source_video
    )
    val summary = buildString {
        append(formatTypeLabel(media))
        append("  ·  ")
        append(media.width)
        append(" × ")
        append(media.height)
        append("  ·  ")
        append(formatBytes(media.sizeBytes))
        if (media.kind == MediaKind.VIDEO) {
            append("  ·  ")
            append(formatDuration(media.durationMs))
        }
    }

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .clip(AppCardShape),
            shape = AppCardShape,
            color = Color.Black
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    media.kind == MediaKind.IMAGE -> ImagePreview(media)
                    !isExporting -> VideoPreview(media.uri)
                    else -> PausedVideoPreview()
                }

                PreviewTag(
                    text = typeLabel,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                )
                PreviewTag(
                    text = media.width.toString() + " × " + media.height.toString(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                )
                if (media.kind == MediaKind.VIDEO) {
                    PreviewTag(
                        text = formatDuration(media.durationMs),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            text = media.displayName,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = summary,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PreviewTag(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.58f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun ImagePreview(media: MediaInfo) {
    val context = LocalContext.current
    val description = stringResource(R.string.image_preview_desc)
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
        initialValue = null,
        key1 = media.uri
    ) {
        value = ImagePreviewLoader.load(context, media.uri)?.asImageBitmap()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            androidx.compose.foundation.Image(
                bitmap = image,
                contentDescription = media.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White.copy(alpha = 0.8f),
                strokeWidth = 2.dp
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPreview(uri: Uri) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    val description = stringResource(R.string.video_preview_desc)
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                useController = true
                controllerShowTimeoutMs = 3000
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                setBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
            }
        },
        update = { view -> view.player = player },
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
    )
}

@Composable
private fun PausedVideoPreview() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.preview_paused_export),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ResolutionCard(
    state: EditorUiState,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    onPresetSelected: (ResolutionPreset) -> Unit,
    onToggleAspectLock: () -> Unit,
    onModeSelected: (ResizeMode) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val media = state.media ?: return
    val enabled = !state.isExporting
    val target = state.targetSize
    val maxDimension = ResolutionMath.maxDimension(state.isVideo)
    val sizeError = when {
        target == null -> stringResource(R.string.error_empty_size)
        target.width < ResolutionMath.MIN_DIMENSION ||
            target.height < ResolutionMath.MIN_DIMENSION ||
            target.width > maxDimension ||
            target.height > maxDimension -> stringResource(
            R.string.error_size_range,
            ResolutionMath.MIN_DIMENSION,
            maxDimension
        )
        !state.isVideo &&
            target.width.toLong() * target.height > ResolutionMath.MAX_IMAGE_PIXELS ->
            stringResource(
                R.string.error_pixel_limit,
                (ResolutionMath.MAX_IMAGE_PIXELS / 1_000_000L).toInt()
            )
        else -> null
    }

    AppCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.resolution_title),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(11.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionPreset.entries.forEach { preset ->
                    if (preset == ResolutionPreset.CUSTOM && state.preset != ResolutionPreset.CUSTOM) {
                        return@forEach
                    }
                    AppChoice(
                        label = presetLabel(preset),
                        selected = state.preset == preset,
                        enabled = enabled && preset != ResolutionPreset.CUSTOM,
                        onClick = { onPresetSelected(preset) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                DimensionField(
                    label = stringResource(R.string.label_width),
                    value = state.widthText,
                    enabled = enabled,
                    imeAction = ImeAction.Next,
                    onValueChange = onWidthChange,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "×",
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant
                )
                DimensionField(
                    label = stringResource(R.string.label_height),
                    value = state.heightText,
                    enabled = enabled,
                    imeAction = ImeAction.Done,
                    onValueChange = onHeightChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .toggleable(
                        value = state.lockAspect,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onToggleAspectLock() }
                    )
                    .padding(horizontal = 2.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (state.lockAspect) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                    contentDescription = null,
                    tint = if (enabled) colors.primary else colors.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(
                        if (state.lockAspect) R.string.lock_aspect else R.string.unlock_aspect
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurface
                )
                Spacer(Modifier.weight(1f))
                AppCheckSquare(checked = state.lockAspect, enabled = enabled)
            }

            if (sizeError != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sizeError,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.error
                )
            } else if (state.isVideo) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.video_even_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = !state.lockAspect,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = colors.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.mode_section),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResizeMode.entries.forEach { mode ->
                            AppChoice(
                                label = modeLabel(mode),
                                selected = state.mode == mode,
                                enabled = enabled,
                                onClick = { onModeSelected(mode) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = modeDescription(state.mode),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DimensionField(
    label: String,
    value: String,
    enabled: Boolean,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            trailingIcon = {
                Text(
                    text = stringResource(R.string.unit_px),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = imeAction
            ),
            shape = AppControlShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                disabledBorderColor = colors.outlineVariant.copy(alpha = 0.6f),
                cursorColor = colors.primary
            )
        )
    }
}

@Composable
private fun ExportSettingsCard(
    state: EditorUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onFormatSelected: (ImageFormat) -> Unit,
    onQualityChange: (Int) -> Unit,
    onAudioQualitySelected: (AudioQuality) -> Unit
) {
    val media = state.media ?: return
    val colors = MaterialTheme.colorScheme
    val enabled = !state.isExporting
    val summary = if (media.kind == MediaKind.IMAGE) {
        if (state.imageFormat.isLossy) {
            stringResource(
                R.string.settings_image_summary,
                formatLabel(state.imageFormat),
                state.quality
            )
        } else {
            formatLabel(state.imageFormat)
        }
    } else {
        if (!media.hasAudio) {
            stringResource(R.string.settings_no_audio)
        } else if (state.audioQuality == AudioQuality.AUTO) {
            stringResource(R.string.settings_audio_auto)
        } else {
            stringResource(
                R.string.settings_audio_manual,
                state.audioQuality.kbps ?: 0
            )
        }
    }

    AppCard {
        ExpandableHeader(
            title = stringResource(R.string.settings_title),
            summary = summary,
            expanded = expanded,
            onClick = onToggle
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                HorizontalDivider(color = colors.outlineVariant)
                Spacer(Modifier.height(12.dp))
                if (media.kind == MediaKind.IMAGE) {
                    ImageSettings(
                        format = state.imageFormat,
                        quality = state.quality,
                        enabled = enabled,
                        onFormatSelected = onFormatSelected,
                        onQualityChange = onQualityChange
                    )
                } else {
                    VideoAudioSettings(
                        media = media,
                        selected = state.audioQuality,
                        enabled = enabled,
                        onSelected = onAudioQualitySelected
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageSettings(
    format: ImageFormat,
    quality: Int,
    enabled: Boolean,
    onFormatSelected: (ImageFormat) -> Unit,
    onQualityChange: (Int) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.format_section),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant
    )
    Spacer(Modifier.height(7.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ImageFormat.entries.forEach { candidate ->
            AppChoice(
                label = formatLabel(candidate),
                selected = format == candidate,
                enabled = enabled,
                onClick = { onFormatSelected(candidate) },
                modifier = Modifier.weight(1f)
            )
        }
    }
    if (format.isLossy) {
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.quality_label),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.quality_value, quality),
                style = MaterialTheme.typography.labelMedium,
                color = colors.primary,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = quality.toFloat(),
            onValueChange = { onQualityChange(it.roundToInt()) },
            valueRange = 40f..100f,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = colors.primary,
                activeTrackColor = colors.primary,
                inactiveTrackColor = colors.surfaceVariant
            )
        )
    } else {
        Spacer(Modifier.height(9.dp))
        Text(
            text = stringResource(R.string.png_lossless_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
private fun VideoAudioSettings(
    media: MediaInfo,
    selected: AudioQuality,
    enabled: Boolean,
    onSelected: (AudioQuality) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = stringResource(R.string.audio_section),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSurfaceVariant
    )
    Spacer(Modifier.height(7.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AudioQuality.entries.forEach { quality ->
            AppChoice(
                label = audioQualityLabel(quality),
                selected = selected == quality,
                enabled = enabled,
                onClick = { onSelected(quality) }
            )
        }
    }
    Spacer(Modifier.height(9.dp))
    Text(
        text = if (selected == AudioQuality.AUTO) {
            stringResource(R.string.settings_audio_auto_hint)
        } else {
            stringResource(R.string.settings_audio_manual_hint, selected.kbps ?: 0)
        },
        style = MaterialTheme.typography.labelSmall,
        color = colors.onSurfaceVariant
    )
    if (selected != AudioQuality.AUTO) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.audio_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.78f)
        )
    }
    if (!media.hasAudio) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_audio_no_track),
            style = MaterialTheme.typography.labelSmall,
            color = colors.tertiary
        )
    }
}

@Composable
private fun ExportSection(
    state: EditorUiState,
    media: MediaInfo,
    targetValid: Boolean,
    onExport: () -> Unit,
    onCancelExport: () -> Unit,
    onOpenResult: (ExportResult) -> Unit,
    onShareResult: (ExportResult) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column {
        when (val status = state.exportStatus) {
            is ExportStatus.Running -> {
                ExportProgressCard(status = status, onCancel = onCancelExport)
                Spacer(Modifier.height(12.dp))
            }
            is ExportStatus.Success -> {
                ExportSuccessCard(
                    result = status.result,
                    onOpen = { onOpenResult(status.result) },
                    onShare = { onShareResult(status.result) }
                )
                Spacer(Modifier.height(12.dp))
            }
            ExportStatus.Idle -> Unit
        }

        if (!state.isExporting) {
            PrimaryActionButton(
                label = stringResource(R.string.export_button),
                icon = Icons.Rounded.Download,
                onClick = onExport,
                enabled = targetValid,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.output_save_to,
                    stringResource(
                        if (media.kind == MediaKind.IMAGE) {
                            R.string.export_location_images
                        } else {
                            R.string.export_location_videos
                        }
                    )
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ExportProgressCard(
    status: ExportStatus.Running,
    onCancel: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        targetValue = status.progress,
        animationSpec = tween(durationMillis = 320),
        label = "export_progress"
    )
    AppCard {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.exporting_button),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(R.string.export_progress, (progress * 100).roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primary,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = colors.primary,
                trackColor = colors.surfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stageLabel(status.stage),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                SecondaryActionButton(
                    label = stringResource(R.string.cancel_export),
                    onClick = onCancel
                )
            }
        }
    }
}

@Composable
private fun ExportSuccessCard(
    result: ExportResult,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppCardShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.30f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.export_saved_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(
                    R.string.export_output_detail,
                    result.width,
                    result.height,
                    formatBytes(result.sizeBytes)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = AppControlShape,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.action_open),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = AppControlShape,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant)
                ) {
                    Text(
                        text = stringResource(R.string.action_share),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun presetLabel(preset: ResolutionPreset): String {
    return stringResource(
        when (preset) {
            ResolutionPreset.ORIGINAL -> R.string.preset_original
            ResolutionPreset.UHD_4K -> R.string.preset_4k
            ResolutionPreset.QHD_2K -> R.string.preset_2k
            ResolutionPreset.FHD_1080 -> R.string.preset_1080p
            ResolutionPreset.HD_720 -> R.string.preset_720p
            ResolutionPreset.SD_480 -> R.string.preset_480p
            ResolutionPreset.CUSTOM -> R.string.preset_custom
        }
    )
}

@Composable
private fun modeLabel(mode: ResizeMode): String {
    return stringResource(
        when (mode) {
            ResizeMode.FIT -> R.string.mode_fit
            ResizeMode.CROP -> R.string.mode_crop
            ResizeMode.STRETCH -> R.string.mode_stretch
        }
    )
}

@Composable
private fun modeDescription(mode: ResizeMode): String {
    return stringResource(
        when (mode) {
            ResizeMode.FIT -> R.string.mode_fit_desc
            ResizeMode.CROP -> R.string.mode_crop_desc
            ResizeMode.STRETCH -> R.string.mode_stretch_desc
        }
    )
}

@Composable
private fun formatLabel(format: ImageFormat): String {
    return stringResource(
        when (format) {
            ImageFormat.JPEG -> R.string.format_jpeg
            ImageFormat.PNG -> R.string.format_png
            ImageFormat.WEBP -> R.string.format_webp
        }
    )
}

@Composable
private fun audioQualityLabel(quality: AudioQuality): String {
    return stringResource(
        when (quality) {
            AudioQuality.AUTO -> R.string.audio_quality_auto
            AudioQuality.KBPS_96 -> R.string.audio_quality_96
            AudioQuality.KBPS_128 -> R.string.audio_quality_128
            AudioQuality.KBPS_192 -> R.string.audio_quality_192
            AudioQuality.KBPS_256 -> R.string.audio_quality_256
            AudioQuality.KBPS_320 -> R.string.audio_quality_320
        }
    )
}

@Composable
private fun stageLabel(stage: ExportStage): String {
    return stringResource(
        when (stage) {
            ExportStage.PREPARING -> R.string.export_stage_preparing
            ExportStage.DECODING -> R.string.export_stage_decoding
            ExportStage.DRAWING -> R.string.export_stage_drawing
            ExportStage.ENCODING -> R.string.export_stage_encoding
            ExportStage.SAVING -> R.string.export_stage_saving
        }
    )
}
