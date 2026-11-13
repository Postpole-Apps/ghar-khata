package com.postpole.gharkhata.ui.pro

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The crown from design/mockups/06-pro-sheet.png: three points on a band. Not part of Material
 * Icons, so it is drawn here as a 24 dp vector. Tint it at the call site.
 */
internal val CrownIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Crown",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // Body: left point, valley, centre point, valley, right point, then the flat bottom.
        path(fill = SolidColor(Color.Black)) {
            moveTo(3f, 7f)
            lineTo(8f, 11f)
            lineTo(12f, 4f)
            lineTo(16f, 11f)
            lineTo(21f, 7f)
            lineTo(19f, 16f)
            lineTo(5f, 16f)
            close()
        }
        // Band.
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 18f)
            lineTo(19f, 18f)
            lineTo(19f, 20.5f)
            lineTo(5f, 20.5f)
            close()
        }
    }.build()
}
