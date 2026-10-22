package dev.orestegabo.portfolio.investments

import org.jetbrains.compose.resources.ExperimentalResourceApi
import portfolio.app.shared.generated.resources.Res

/** One resource tree packaged by Compose for both mobile applications. */
@OptIn(ExperimentalResourceApi::class)
suspend fun readSharedAsset(name: String): ByteArray {
    require(!name.contains("..") && !name.startsWith('/'))
    return Res.readBytes("files/$name")
}
