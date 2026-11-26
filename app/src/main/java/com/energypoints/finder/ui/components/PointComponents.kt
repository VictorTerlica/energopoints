@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.energypoints.finder.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.energypoints.finder.data.Point
import com.energypoints.finder.ui.theme.BrandNavy
import com.energypoints.finder.ui.theme.RossetiGreen
import com.energypoints.finder.ui.theme.RossetiGreenContainer
import com.energypoints.finder.ui.theme.RossetiGreenContainerDark
import com.energypoints.finder.ui.theme.SskBlue
import com.energypoints.finder.ui.theme.SskBlueContainer
import com.energypoints.finder.ui.theme.SskBlueContainerDark

/** Векторная молния — без зависимости от дополнительных иконок. */
private val BoltIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Bolt",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(13.5f, 2f)
            lineTo(5f, 13.5f)
            horizontalLineTo(11f)
            lineTo(9.5f, 22f)
            lineTo(19f, 10f)
            horizontalLineTo(12.5f)
            close()
        }
    }.build()
}

fun colorFromHex(hex: String): Color =
    Color(AndroidColor.parseColor(hex))

@Composable
fun GradientIcon(
    colorHex: String,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val base = colorFromHex(colorHex)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(
                Brush.linearGradient(
                    listOf(base, lerp(base, Color.White, 0.25f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = BoltIcon,
            contentDescription = null,
            tint = boltTint(base),
            modifier = Modifier.size(size * 0.58f)
        )
    }
}

private fun boltTint(background: Color): Color =
    if (background.luminance() > 0.45f) BrandNavy else Color.White

@Composable
fun PointCard(
    point: Point,
    note: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientIcon(
                colorHex = point.resolvedColor,
                size = 48.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${point.district} • ${point.typeLabel}",
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!point.ownership.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        OwnershipBadge(ownership = point.ownership)
                    }
                }
                if (!note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = note.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun OwnershipBadge(ownership: String?) {
    val label = ownership?.trim().orEmpty()
    if (label.isEmpty()) return

    val darkTheme = isSystemInDarkTheme()
    val (background, foreground) = when {
        label.contains("РОС", ignoreCase = true) -> {
            if (darkTheme) {
                RossetiGreenContainerDark to Color(0xFFA5D6A7)
            } else {
                RossetiGreenContainer to RossetiGreen
            }
        }

        label.contains("ССК", ignoreCase = true) -> {
            if (darkTheme) {
                SskBlueContainerDark to Color(0xFF90CAF9)
            } else {
                SskBlueContainer to SskBlue
            }
        }

        else -> MaterialTheme.colorScheme.surfaceVariant to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun IssuesDialog(
    issues: List<String>,
    onDismiss: () -> Unit
) {
    if (issues.isEmpty()) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null) },
        title = { Text("Проверка данных") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Это информационные замечания. Они не блокируют " +
                        "импорт и сохранение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                issues.forEach { issue ->
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text("•", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = issue,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}
