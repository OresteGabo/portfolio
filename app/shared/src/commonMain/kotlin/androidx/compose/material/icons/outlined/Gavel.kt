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

public val Icons.Outlined.Gavel: ImageVector
    get() {
        if (_gavel != null) {
            return _gavel!!
        }
        _gavel = materialIcon(name = "Outlined.Gavel") {
            materialPath {
                moveTo(1.0f, 21.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(1.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(5.24f, 8.07f)
                lineToRelative(2.83f, -2.83f)
                lineToRelative(14.14f, 14.14f)
                lineToRelative(-2.83f, 2.83f)
                lineTo(5.24f, 8.07f)
                close()
                moveTo(12.32f, 1.0f)
                lineToRelative(5.66f, 5.66f)
                lineToRelative(-2.83f, 2.83f)
                lineToRelative(-5.66f, -5.66f)
                lineTo(12.32f, 1.0f)
                close()
                moveTo(3.83f, 9.48f)
                lineToRelative(5.66f, 5.66f)
                lineToRelative(-2.83f, 2.83f)
                lineTo(1.0f, 12.31f)
                lineToRelative(2.83f, -2.83f)
                close()
            }
        }
        return _gavel!!
    }

private var _gavel: ImageVector? = null
