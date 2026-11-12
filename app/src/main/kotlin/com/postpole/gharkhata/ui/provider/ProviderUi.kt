package com.postpole.gharkhata.ui.provider

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.SoupKitchen
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.postpole.gharkhata.R
import com.postpole.gharkhata.data.ProviderType

internal fun ProviderType.icon(): ImageVector = when (this) {
    ProviderType.MILK -> Icons.Outlined.LocalDrink
    ProviderType.MAID -> Icons.Outlined.CleaningServices
    ProviderType.NEWSPAPER -> Icons.Outlined.Newspaper
    ProviderType.WATER_CAN -> Icons.Outlined.WaterDrop
    ProviderType.COOK -> Icons.Outlined.SoupKitchen
    ProviderType.DRIVER -> Icons.Outlined.DirectionsCar
    ProviderType.OTHER -> Icons.Outlined.MoreHoriz
}

/**
 * Scalloped / clover shape behind a provider's icon, as in the mockup avatars. The pinned material3 does not
 * ship `MaterialShapes`, so these are built with graphics-shapes `RoundedPolygon` (same idea, same look).
 */
@Composable
internal fun ProviderType.avatarShape(): Shape = remember(this) {
    when (this) {
        ProviderType.MILK -> cookie(9)
        ProviderType.MAID -> cookie(6)
        ProviderType.NEWSPAPER -> clover(4)
        ProviderType.WATER_CAN -> cookie(7)
        ProviderType.COOK -> clover(5)
        ProviderType.DRIVER -> cookie(12)
        ProviderType.OTHER -> cookie(8)
    }
}

private fun cookie(sides: Int): Shape =
    RoundedPolygonShape(RoundedPolygon.star(sides, innerRadius = 0.86f, rounding = CornerRounding(0.28f), innerRounding = CornerRounding(0.28f)))

private fun clover(leaves: Int): Shape =
    RoundedPolygonShape(RoundedPolygon.star(leaves, innerRadius = 0.6f, rounding = CornerRounding(0.4f), innerRounding = CornerRounding(0.25f)))

/** Soft scalloped badge (rate-change icon). */
internal val BadgeShape: Shape by lazy { cookie(10) }

internal class RoundedPolygonShape(polygon: RoundedPolygon) : Shape {
    private val normalized = polygon.normalized()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = normalized.toPath().asComposePath()
        path.transform(Matrix().apply { scale(size.width, size.height) })
        return Outline.Generic(path)
    }
}

@Composable
internal fun ProviderAvatar(type: ProviderType, container: Color, content: Color, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(type.avatarShape()).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.icon(), contentDescription = null, tint = content, modifier = Modifier.size(size / 2))
    }
}

/**
 * A pill that is visually [PILL_HEIGHT] tall but keeps a 48 dp touch target: the clickable box takes
 * the minimum interactive size and the coloured pill is drawn inside it.
 */
@Composable
internal fun PillButton(
    onClick: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier.minimumInteractiveComponentSize().clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.height(PILL_HEIGHT).clip(CircleShape).background(container).padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

val PILL_HEIGHT = 32.dp

/** Short schedule text for the list subtitle: "daily", "Mon–Sat", "Mon, Wed, Fri" or "on demand". */
@Composable
internal fun scheduleLabel(mask: Int): String {
    val days = SHORT_DAY_RES.map { stringResource(it) }
    val on = days.indices.filter { mask and (1 shl it) != 0 }
    return when {
        on.isEmpty() -> stringResource(R.string.schedule_on_demand)
        on.size == 7 -> stringResource(R.string.schedule_daily)
        on.size >= 3 && on.last() - on.first() == on.size - 1 ->
            stringResource(R.string.schedule_range, days[on.first()], days[on.last()])
        else -> on.joinToString(stringResource(R.string.schedule_list_separator)) { days[it] }
    }
}

private val SHORT_DAY_RES = listOf(
    R.string.day_short_mon, R.string.day_short_tue, R.string.day_short_wed, R.string.day_short_thu,
    R.string.day_short_fri, R.string.day_short_sat, R.string.day_short_sun,
)

/** Next value of the usual-quantity stepper; [direction] is +1 or -1. Never goes to zero or below. */
internal fun stepQty(current: String, unitLabel: String, direction: Int): String {
    val step = if (unitLabel.trim().equals("L", ignoreCase = true)) 0.5 else 1.0
    val value = current.trim().toDoubleOrNull() ?: 1.0
    val next = (value + direction * step).coerceAtLeast(step)
    return if (next % 1.0 == 0.0) next.toLong().toString() else next.toString()
}
