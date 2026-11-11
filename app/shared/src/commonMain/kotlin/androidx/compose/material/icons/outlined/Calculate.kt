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

public val Icons.Outlined.Calculate: ImageVector
    get() {
        if (_calculate != null) {
            return _calculate!!
        }
        _calculate = materialIcon(name = "Outlined.Calculate") {
            materialPath {
                moveTo(19.0f, 3.0f)
                horizontalLineTo(5.0f)
                curveTo(3.9f, 3.0f, 3.0f, 3.9f, 3.0f, 5.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveTo(21.0f, 3.9f, 20.1f, 3.0f, 19.0f, 3.0f)
                close()
                moveTo(19.0f, 19.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(19.0f)
                close()
            }
            materialPath {
                moveTo(6.25f, 7.72f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(-5.0f)
                close()
            }
            materialPath {
                moveTo(13.0f, 15.75f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(-5.0f)
                close()
            }
            materialPath {
                moveTo(13.0f, 13.25f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(1.5f)
                horizontalLineToRelative(-5.0f)
                close()
            }
            materialPath {
                moveTo(8.0f, 18.0f)
                lineToRelative(1.5f, 0.0f)
                lineToRelative(0.0f, -2.0f)
                lineToRelative(2.0f, 0.0f)
                lineToRelative(0.0f, -1.5f)
                lineToRelative(-2.0f, 0.0f)
                lineToRelative(0.0f, -2.0f)
                lineToRelative(-1.5f, 0.0f)
                lineToRelative(0.0f, 2.0f)
                lineToRelative(-2.0f, 0.0f)
                lineToRelative(0.0f, 1.5f)
                lineToRelative(2.0f, 0.0f)
                close()
            }
            materialPath {
                moveTo(14.09f, 10.95f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(1.06f, -1.06f)
                lineToRelative(-1.41f, -1.42f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.06f, -1.06f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(-1.41f, -1.41f)
                lineToRelative(-1.06f, 1.06f)
                lineToRelative(1.41f, 1.41f)
                lineToRelative(-1.41f, 1.42f)
                close()
            }
        }
        return _calculate!!
    }

private var _calculate: ImageVector? = null
