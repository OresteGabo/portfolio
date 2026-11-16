package dev.orestegabo.portfolio.investments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import dev.orestegabo.portfolio.design.StructColors
import kotlin.math.max
import kotlin.math.pow

private val camera = BrandVector(0f, 0f, 1000f)
private val keyLight = BrandVector(-300f, 430f, 650f)
private val fillLight = BrandVector(450f, 80f, 400f)
private val rimLight = BrandVector(600f, 100f, -300f)
private val gold = lerp(StructColors.Brand.olive, StructColors.Brand.cream, .36f)
private val reflection = lerp(StructColors.Brand.cream, StructColors.Brand.canvas, .6f)

/** Fixed studio lights, evaluated against rotating surface normals and the viewer direction. */
internal fun initialMetalColor(point: BrandVector, normal: BrandVector, dark: Boolean): Color {
    val light = (keyLight - point).unit()
    val fill = (fillLight - point).unit()
    val rim = (rimLight - point).unit()
    val view = (camera - point).unit()
    val half = (light + view).unit()
    val diffuse = (if (dark) .26f else .30f) + .62f * max(0f, normal.dot(light)) + .24f * max(0f, normal.dot(fill))
    val rimHalf = (rim + view).unit()
    val specular = .9f * max(0f, normal.dot(half)).pow(64f) + .16f * max(0f, normal.dot(half)).pow(12f) +
        .8f * max(0f, normal.dot(rimHalf)).pow(36f)
    return Color(
        (gold.red * diffuse + reflection.red * specular).coerceIn(0f, 1f),
        (gold.green * diffuse + reflection.green * specular).coerceIn(0f, 1f),
        (gold.blue * diffuse + reflection.blue * specular).coerceIn(0f, 1f),
    )
}

/** Projects a closed, bevelled solid. Sidewalls remain visible at 90° and 270°. */
internal fun DrawScope.drawRotatingInziraInitial(degrees: Float, dark: Boolean) {
    val (cosine, sine) = InziraInitialMesh.rotation(degrees)
    val rotated = InziraInitialMesh.faces.map { face ->
        face.corners.map { BrandMeshCorner(it.position.turn(cosine, sine), it.normal.turn(cosine, sine)) }
    }
    // A single filled silhouette avoids dark seams where projected shadow triangles overlap.
    val shadow = Path()
    for (face in rotated) {
        val projected = face.map { corner ->
            // A soft screen-space contact shadow follows the solid's projected silhouette.
            InziraInitialMesh.project(corner.position) + Offset(4f, 7f)
        }
        val signedArea = projected.indices.sumOf { i ->
            val a = projected[i]; val b = projected[(i + 1) % projected.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }
        val polygon = if (signedArea > 0) projected else projected.reversed()
        shadow.moveTo(polygon[0].x, polygon[0].y)
        polygon.drop(1).forEach { shadow.lineTo(it.x, it.y) }
        shadow.close()
    }
    for (spread in 3 downTo 1) translate(0f, 2f) {
        drawPath(shadow, StructColors.Fixed.black, alpha = if (dark) .025f else .018f, style = Stroke(spread * 3f))
    }
    drawPath(shadow, StructColors.Fixed.black, alpha = if (dark) .20f else .10f)

    val visible = rotated.filter { face ->
        val center = face.fold(BrandVector(0f, 0f, 0f)) { sum, corner -> sum + corner.position } * (1f / face.size)
        val normal = face.fold(BrandVector(0f, 0f, 0f)) { sum, corner -> sum + corner.normal }.unit()
        normal.dot(camera - center) > 0f
    }.sortedBy { face -> face.sumOf { it.position.z.toDouble() } / face.size }
    val positions = ArrayList<Offset>(visible.size * 6)
    val colors = ArrayList<Color>(visible.size * 6)
    for (face in visible) {
        for (triangle in 1 until face.size - 1) {
            for (index in listOf(0, triangle, triangle + 1)) {
                val corner = face[index]
                positions += InziraInitialMesh.project(corner.position)
                colors += initialMetalColor(corner.position, corner.normal, dark)
            }
        }
    }
    if (positions.isNotEmpty()) drawIntoCanvas { canvas ->
        val vertices = Vertices(VertexMode.Triangles, positions, textureCoordinates = positions,
            colors = colors, indices = positions.indices.toList())
        canvas.drawVertices(vertices, BlendMode.Modulate, Paint().apply { color = StructColors.Fixed.white })
    }
}
