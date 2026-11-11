/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.material.icons.outlined

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Outlined.AreaChart: ImageVector
    get() {
        if (_areaChart != null) {
            return _areaChart!!
        }
        _areaChart = materialIcon(name = "Outlined.AreaChart") {
            materialPath {
                moveTo(17.0f, 7.0f)
                lineToRelative(-5.0f, -4.0f)
                lineToRelative(-5.0f, 7.0f)
                lineTo(3.0f, 7.0f)
                verticalLineToRelative(13.0f)
                horizontalLineToRelative(18.0f)
                verticalLineTo(7.0f)
                horizontalLineTo(17.0f)
                close()
                moveTo(19.0f, 16.95f)
                lineToRelative(-7.0f, -5.45f)
                lineTo(8.0f, 17.0f)
                lineToRelative(-3.0f, -2.4f)
                verticalLineTo(11.0f)
                lineToRelative(2.44f, 1.83f)
                lineToRelative(4.96f, -6.95f)
                lineTo(16.3f, 9.0f)
                horizontalLineTo(19.0f)
                verticalLineTo(16.95f)
                close()
            }
        }
        return _areaChart!!
    }

private var _areaChart: ImageVector? = null
