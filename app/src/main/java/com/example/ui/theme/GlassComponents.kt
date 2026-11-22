package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Ambient Dark Midnight Obsidian canvas with layered Cyan/Violet optical light pools.
 */
@Composable
fun CyberGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ObsidianDeep, ObsidianMid, ObsidianDeep)
                )
            )
            .drawBehind {
                // Top-left electric cyan atmospheric bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.14f),
                            NeonCyan.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.12f, size.height * 0.08f),
                        radius = size.width * 0.85f
                    ),
                    center = Offset(size.width * 0.12f, size.height * 0.08f),
                    radius = size.width * 0.85f
                )
                // Mid-right cyber violet bloom
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CyberViolet.copy(alpha = 0.16f),
                            CyberViolet.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.92f, size.height * 0.42f),
                        radius = size.width * 0.78f
                    ),
                    center = Offset(size.width * 0.92f, size.height * 0.42f),
                    radius = size.width * 0.78f
                )
                // Bottom-left subtle emerald privacy glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            EmeraldGlow.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.2f, size.height * 0.92f),
                        radius = size.width * 0.65f
                    ),
                    center = Offset(size.width * 0.2f, size.height * 0.92f),
                    radius = size.width * 0.65f
                )
            },
        content = content
    )
}

/**
 * Translucent Liquid Glassmorphism squircle card with optical specular refraction border.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    highlightAccent: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val surfaceBrush = remember(highlightAccent) {
        if (highlightAccent != null) {
            Brush.linearGradient(
                colors = listOf(
                    highlightAccent.copy(alpha = 0.14f),
                    GlassSurfaceFill,
                    GlassSurfaceSubtle
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    GlassSurfaceHigh,
                    GlassSurfaceFill,
                    GlassSurfaceSubtle
                )
            )
        }
    }

    val borderBrush = remember(highlightAccent) {
        Brush.linearGradient(
            colors = listOf(
                (highlightAccent ?: Color.White).copy(alpha = 0.30f),
                GlassBorderMedium,
                GlassBorderSubtle,
                (highlightAccent ?: NeonCyan).copy(alpha = 0.16f)
            )
        )
    }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = NeonCyanBright),
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .then(clickModifier)
            .padding(contentPadding),
        content = content
    )
}

/**
 * Liquid Pill Action Button with Cyan-to-Violet linear gradient, top optical specular sheen,
 * spring press physics, and accessible 48.dp touch target.
 */
@Composable
fun LiquidPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isPrimary: Boolean = true,
    accentColors: List<Color> = listOf(NeonCyan, CyberViolet),
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pill_scale"
    )
    val shape = RoundedCornerShape(50.dp)

    val backgroundBrush = if (isPrimary && enabled) {
        Brush.horizontalGradient(colors = accentColors)
    } else if (!enabled) {
        Brush.horizontalGradient(colors = listOf(GlassSurfaceSubtle, GlassSurfaceSubtle))
    } else {
        Brush.linearGradient(colors = listOf(GlassSurfaceHigh, GlassSurfaceFill))
    }

    val borderBrush = if (isPrimary && enabled) {
        Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.45f),
                Color.White.copy(alpha = 0.10f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(GlassBorderLight, GlassBorderSubtle)
        )
    }

    Box(
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(backgroundBrush)
            .border(width = 1.dp, brush = borderBrush, shape = shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(color = Color.White),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isPrimary && enabled) Color.White else if (enabled) TextPrimary else TextMuted,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (isPrimary && enabled) Color.White else if (enabled) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Circular translucent glass icon button with 48.dp touch target.
 */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    accentGlow: Color? = null
) {
    val shape = CircleShape
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(shape)
            .background(
                if (accentGlow != null) accentGlow.copy(alpha = 0.16f) else GlassSurfaceFill
            )
            .border(
                width = 1.dp,
                color = accentGlow?.copy(alpha = 0.35f) ?: GlassBorderMedium,
                shape = shape
            )
            .clickable(
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accentGlow ?: NeonCyan),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * Frosted glass search bar with subtle luminous outline and instant clear action.
 */
@Composable
fun GlassSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val shape = RoundedCornerShape(24.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(GlassSurfaceHigh, GlassSurfaceFill)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        if (query.isNotEmpty()) NeonCyan.copy(alpha = 0.55f) else GlassBorderLight,
                        GlassBorderSubtle
                    )
                ),
                shape = shape
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = if (query.isNotEmpty()) NeonCyanBright else TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                cursorBrush = SolidColor(NeonCyanBright),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("clear_search_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_clear_search),
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Luminous status badge pill for active clones, Work Profile status, and anti-detect shields.
 */
@Composable
fun GlassBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    pulsingDot: Boolean = false,
    icon: ImageVector? = null
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badge_pulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val shape = RoundedCornerShape(50.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.15f))
            .border(width = 1.dp, color = color.copy(alpha = 0.40f), shape = shape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (pulsingDot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = dotAlpha))
            )
        } else if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

data class GlassNavItem(
    val id: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

/**
 * Floating 28.dp rounded Liquid Glass bottom navigation pill respecting system navigation bars.
 */
@Composable
fun FloatingGlassNavBar(
    items: List<GlassNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val barShape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(barShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            ObsidianElevated.copy(alpha = 0.92f),
                            ObsidianDeep.copy(alpha = 0.94f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            NeonCyan.copy(alpha = 0.35f),
                            GlassBorderMedium,
                            CyberViolet.copy(alpha = 0.35f)
                        )
                    ),
                    shape = barShape
                )
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val isSelected = item.id == selectedId
                val pillShape = RoundedCornerShape(22.dp)
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else TextSecondary,
                    label = "nav_text_color"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(pillShape)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                NeonCyan.copy(alpha = 0.28f),
                                                CyberViolet.copy(alpha = 0.30f)
                                            )
                                        )
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = NeonCyan.copy(alpha = 0.45f),
                                        shape = pillShape
                                    )
                            } else {
                                Modifier
                            }
                        )
                        .clickable(
                            role = Role.Tab,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = NeonCyanBright),
                            onClick = { onSelect(item.id) }
                        )
                        .padding(vertical = 8.dp, horizontal = 6.dp)
                        .testTag(item.testTag),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) NeonCyanBright else TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
