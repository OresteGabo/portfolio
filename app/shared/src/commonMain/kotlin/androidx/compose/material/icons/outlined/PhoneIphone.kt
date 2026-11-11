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

public val Icons.Outlined.PhoneIphone: ImageVector
    get() {
        if (_phoneIphone != null) {
            return _phoneIphone!!
        }
        _phoneIphone = materialIcon(name = "Outlined.PhoneIphone") {
            materialPath {
                moveTo(15.5f, 1.0f)
                horizontalLineToRelative(-8.0f)
                curveTo(6.12f, 1.0f, 5.0f, 2.12f, 5.0f, 3.5f)
                verticalLineToRelative(17.0f)
                curveTo(5.0f, 21.88f, 6.12f, 23.0f, 7.5f, 23.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.38f, 0.0f, 2.5f, -1.12f, 2.5f, -2.5f)
                verticalLineToRelative(-17.0f)
                curveTo(18.0f, 2.12f, 16.88f, 1.0f, 15.5f, 1.0f)
                close()
                moveTo(11.5f, 22.0f)
                curveToRelative(-0.83f, 0.0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveToRelative(0.67f, -1.5f, 1.5f, -1.5f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
                moveTo(16.0f, 18.0f)
                lineTo(7.0f, 18.0f)
                lineTo(7.0f, 4.0f)
                horizontalLineToRelative(9.0f)
                verticalLineToRelative(14.0f)
                close()
            }
        }
        return _phoneIphone!!
    }

private var _phoneIphone: ImageVector? = null
