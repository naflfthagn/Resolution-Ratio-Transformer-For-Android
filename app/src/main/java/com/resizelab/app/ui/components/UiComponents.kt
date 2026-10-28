package com.resizelab.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val AppCardShape = RoundedCornerShape(14.dp)
val AppControlShape = RoundedCornerShape(10.dp)
val AppChipShape = RoundedCornerShape(8.dp)

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppCardShape,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Column(content = content)
    }
}

@Composable
fun PrimaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    loading: Boolean = false
) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = enabled && !loading,
        shape = AppControlShape,
        contentPadding = PaddingValues(horizontal = 18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            disabledContainerColor = colors.primary.copy(alpha = 0.45f),
            disabledContentColor = colors.onPrimary.copy(alpha = 0.7f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.onPrimary,
                strokeWidth = 2.dp
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp)
            )
        }
        if (loading || icon != null) Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        enabled = enabled,
        shape = AppControlShape,
        contentPadding = PaddingValues(horizontal = 12.dp),
        border = BorderStroke(1.dp, colors.outlineVariant)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface
        )
    }
}

@Composable
fun AppChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(AppChipShape)
            .background(
                if (selected) {
                    colors.primaryContainer
                } else {
                    colors.surfaceVariant.copy(alpha = 0.30f)
                }
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    colors.primary.copy(alpha = 0.55f)
                } else {
                    colors.outlineVariant
                },
                shape = AppChipShape
            )
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .heightIn(min = 38.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.45f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
fun AppCheckSquare(
    checked: Boolean,
    enabled: Boolean
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(shape)
            .background(
                if (checked) colors.primary else Color.Transparent
            )
            .border(
                width = 1.dp,
                color = if (checked) colors.primary else colors.outline,
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.onPrimary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun RotatingChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "chevron_rotation"
    )
    Icon(
        imageVector = Icons.Rounded.KeyboardArrowDown,
        contentDescription = null,
        modifier = modifier
            .size(20.dp)
            .rotate(rotation),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
fun ExpandableHeader(
    title: String,
    summary: String?,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(6.dp))
        RotatingChevron(expanded = expanded)
    }
}

@Composable
fun AppSquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(AppControlShape)
            .background(colors.surfaceVariant.copy(alpha = 0.35f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(21.dp),
            tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.4f)
        )
    }
}
