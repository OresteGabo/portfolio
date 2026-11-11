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
import kotlin.Deprecated

@Deprecated(
    "Use the AutoMirrored version at Icons.AutoMirrored.Outlined.ReceiptLong",
    ReplaceWith( "Icons.AutoMirrored.Outlined.ReceiptLong",
            "androidx.compose.material.icons.automirrored.outlined.ReceiptLong"),
)
public val Icons.Outlined.ReceiptLong: ImageVector
    get() {
        if (_receiptLong != null) {
            return _receiptLong!!
        }
        _receiptLong = materialIcon(name = "Outlined.ReceiptLong") {
            materialPath {
                moveTo(19.5f, 3.5f)
                lineTo(18.0f, 2.0f)
                lineToRelative(-1.5f, 1.5f)
                lineTo(15.0f, 2.0f)
                lineToRelative(-1.5f, 1.5f)
                lineTo(12.0f, 2.0f)
                lineToRelative(-1.5f, 1.5f)
                lineTo(9.0f, 2.0f)
                lineTo(7.5f, 3.5f)
                lineTo(6.0f, 2.0f)
                verticalLineToRelative(14.0f)
                horizontalLineTo(3.0f)
                verticalLineToRelative(3.0f)
                curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
                verticalLineTo(2.0f)
                lineTo(19.5f, 3.5f)
                close()
                moveTo(15.0f, 20.0f)
                horizontalLineTo(6.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-1.0f)
                horizontalLineToRelative(10.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(19.0f, 19.0f)
                curveToRelative(0.0f, 0.55f, -0.45f, 1.0f, -1.0f, 1.0f)
                reflectiveCurveToRelative(-1.0f, -0.45f, -1.0f, -1.0f)
                verticalLineToRelative(-3.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(11.0f)
                verticalLineTo(19.0f)
                close()
            }
            materialPath {
                moveTo(9.0f, 7.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-6.0f)
                close()
            }
            materialPath {
                moveTo(16.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
            materialPath {
                moveTo(9.0f, 10.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-6.0f)
                close()
            }
            materialPath {
                moveTo(16.0f, 10.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }
        return _receiptLong!!
    }

private var _receiptLong: ImageVector? = null
