/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A custom TwoTone 'Acknowledged' icon representing a double-check (delivered).
 */
val MeshtasticIcons.AcknowledgedTwoTone: ImageVector
    get() {
        if (_acknowledgedTwoTone != null) {
            return _acknowledgedTwoTone!!
        }
        _acknowledgedTwoTone = ImageVector.Builder(
            name = "AcknowledgedTwoTone",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 0.3f,
                strokeLineWidth = 0f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Miter,
                strokeLineMiter = 10f,
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(18f, 7f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-6.34f, 6.34f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(6.34f, -6.34f)
                close()
                moveTo(19.41f, 5.59f)
                lineTo(11f, 14f)
                lineToRelative(-3.41f, -3.41f)
                lineTo(6f, 12f)
                lineToRelative(5f, 5f)
                lineToRelative(10f, -10f)
                lineToRelative(-1.59f, -1.41f)
                close()
                moveTo(5f, 15f)
                lineToRelative(-4.59f, -4.59f)
                lineTo(0f, 11.41f)
                lineTo(5f, 16.41f)
                lineTo(6.41f, 15f)
                lineTo(5f, 13.59f)
                close()
            }
        }.build()
        return _acknowledgedTwoTone!!
    }

private var _acknowledgedTwoTone: ImageVector? = null
