@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package dev.orestegabo.portfolio.investments

import kotlinx.cinterop.*
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

internal actual fun securityDeviceModel(): String = memScoped {
    val info = alloc<utsname>()
    val family = UIDevice.currentDevice.model
    if (uname(info.ptr) == 0) appleDeviceModel(info.machine.toKString(), family) else family
}
