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

public val Icons.Outlined.AccountBalance: ImageVector
    get() {
        if (_accountBalance != null) {
            return _accountBalance!!
        }
        _accountBalance = materialIcon(name = "Outlined.AccountBalance") {
            materialPath {
                moveTo(6.5f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                close()
                moveTo(12.5f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                close()
                moveTo(21.0f, 19.0f)
                lineTo(2.0f, 19.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(19.0f)
                verticalLineToRelative(-2.0f)
                close()
                moveTo(18.5f, 10.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-7.0f)
                close()
                moveTo(11.5f, 3.26f)
                lineTo(16.71f, 6.0f)
                lineTo(6.29f, 6.0f)
                lineToRelative(5.21f, -2.74f)
                moveToRelative(0.0f, -2.26f)
                lineTo(2.0f, 6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(19.0f)
                lineTo(21.0f, 6.0f)
                lineToRelative(-9.5f, -5.0f)
                close()
            }
        }
        return _accountBalance!!
    }

private var _accountBalance: ImageVector? = null
