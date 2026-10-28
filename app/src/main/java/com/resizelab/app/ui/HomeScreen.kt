package com.resizelab.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.resizelab.app.R
import com.resizelab.app.ui.components.AppCard
import com.resizelab.app.ui.components.PrimaryActionButton
import com.resizelab.app.ui.components.RotatingChevron
import com.resizelab.app.ui.theme.Ink
import com.resizelab.app.ui.theme.Lime

@Composable
fun HomeScreen(
    isInspecting: Boolean,
    onPick: () -> Unit
) {
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val brandDescription = stringResource(R.string.cd_app_logo)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 22.dp)
            .padding(top = 18.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(description = brandDescription)
            Spacer(Modifier.width(11.dp))
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.home_header_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(54.dp))
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 32.sp,
                lineHeight = 38.sp
            ),
            color = colors.onBackground
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(0.96f)
        )
        Spacer(Modifier.height(26.dp))

        PrimaryActionButton(
            label = if (isInspecting) {
                stringResource(R.string.loading_media)
            } else {
                stringResource(R.string.home_pick)
            },
            icon = Icons.Rounded.AddPhotoAlternate,
            onClick = onPick,
            enabled = !isInspecting,
            loading = isInspecting,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.home_pick_hint),
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )

        Spacer(Modifier.height(36.dp))

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { helpExpanded = !helpExpanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_help_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                RotatingChevron(expanded = helpExpanded)
            }
            AnimatedVisibility(
                visible = helpExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(color = colors.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    HelpRow(
                        label = stringResource(R.string.source_image),
                        text = stringResource(R.string.home_help_image_body)
                    )
                    Spacer(Modifier.height(8.dp))
                    HelpRow(
                        label = stringResource(R.string.source_video),
                        text = stringResource(R.string.home_help_video_body)
                    )
                    Spacer(Modifier.height(8.dp))
                    HelpRow(
                        label = stringResource(R.string.home_help_export_label),
                        text = stringResource(R.string.home_help_export_body)
                    )
                }
            }
        }

        Spacer(Modifier.height(30.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.home_footer),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun BrandMark(description: String) {
    Canvas(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Ink)
            .semantics { contentDescription = description }
    ) {
        val unit = size.minDimension
        val left = unit * 0.28f
        val top = unit * 0.28f
        val right = unit * 0.72f
        val bottom = unit * 0.72f
        val arm = unit * 0.11f
        val frameWidth = unit * 0.045f
        val frameColor = Color(0xFFF7FAF3)
        drawLine(frameColor, Offset(left, top + arm), Offset(left, top), frameWidth, StrokeCap.Round)
        drawLine(frameColor, Offset(left, top), Offset(left + arm, top), frameWidth, StrokeCap.Round)
        drawLine(
            frameColor,
            Offset(right - arm, bottom),
            Offset(right, bottom),
            frameWidth,
            StrokeCap.Round
        )
        drawLine(
            frameColor,
            Offset(right, bottom),
            Offset(right, bottom - arm),
            frameWidth,
            StrokeCap.Round
        )
        val arrowWidth = unit * 0.055f
        val arrowEnd = Offset(unit * 0.62f, unit * 0.38f)
        drawLine(Lime, Offset(unit * 0.40f, unit * 0.60f), arrowEnd, arrowWidth, StrokeCap.Round)
        drawLine(Lime, Offset(unit * 0.50f, unit * 0.38f), arrowEnd, arrowWidth, StrokeCap.Round)
        drawLine(Lime, arrowEnd, Offset(unit * 0.62f, unit * 0.50f), arrowWidth, StrokeCap.Round)
    }
}

@Composable
private fun HelpRow(label: String, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(46.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
