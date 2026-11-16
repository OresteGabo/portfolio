package dev.orestegabo.portfolio.investments

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Local coordinates use a right-handed space: x right, y up, z towards the viewer. */
internal data class BrandVector(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: BrandVector) = BrandVector(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: BrandVector) = BrandVector(x - other.x, y - other.y, z - other.z)
    operator fun times(value: Float) = BrandVector(x * value, y * value, z * value)
    fun dot(other: BrandVector) = x * other.x + y * other.y + z * other.z
    fun unit(): BrandVector {
        val length = sqrt(dot(this))
        return if (length > .00001f) this * (1f / length) else this
    }
    fun turn(cosine: Float, sine: Float) = BrandVector(x * cosine + z * sine, y, -x * sine + z * cosine)
}

internal data class BrandMeshCorner(val position: BrandVector, val normal: BrandVector)
internal data class BrandMeshFace(val corners: List<BrandMeshCorner>)

/** Immutable mesh built once from the same quadratic outline as the flat logo. No raster texture. */
internal object InziraInitialMesh {
    const val HALF_DEPTH = 14f
    // Stay below the tightest rounded join so bevel offsets cannot fold over themselves.
    private const val BEVEL = 1.2f
    val outline: List<BrandVector> = outlinePoints()
    val faces: List<BrandMeshFace> = buildFaces()

    fun rotation(degrees: Float): Pair<Float, Float> {
        val radians = degrees * (kotlin.math.PI / 180).toFloat()
        return cos(radians) to sin(radians)
    }

    fun project(point: BrandVector): Offset {
        val perspective = 1000f / (1000f - point.z)
        return Offset(220f + point.x * perspective, 200f - point.y * perspective)
    }

    private fun outlinePoints(): List<BrandVector> {
        val points = mutableListOf<BrandVector>()
        var current = Offset.Zero
        fun append(point: Offset) {
            current = point
            points += BrandVector(point.x - 220f, 200f - point.y, 0f)
        }
        for (node in PathParser().parsePathString(InziraBrandGeometry.INITIAL).toNodes()) {
            when (node) {
                is PathNode.MoveTo -> append(Offset(node.x, node.y))
                is PathNode.LineTo -> append(Offset(node.x, node.y))
                is PathNode.QuadTo -> {
                    val start = current
                    for (step in 1..6) {
                        val t = step / 6f
                        val u = 1f - t
                        append(start * (u * u) + Offset(node.x1, node.y1) * (2f * u * t) + Offset(node.x2, node.y2) * (t * t))
                    }
                }
                PathNode.Close -> Unit
                else -> error("Unsupported canonical Inzira outline command")
            }
        }
        // Remove only numerically collinear samples before ear clipping; retain the rounded joins.
        val clean = points.filterIndexed { index, point ->
            abs(cross(points[(index + points.size - 1) % points.size], point, points[(index + 1) % points.size])) > .0001f
        }
        val area = clean.indices.sumOf { i ->
            val a = clean[i]; val b = clean[(i + 1) % clean.size]
            (a.x * b.y - b.x * a.y).toDouble()
        }
        return if (area > 0) clean else clean.reversed()
    }

    private fun buildFaces(): List<BrandMeshFace> {
        val normals = outline.indices.map { i ->
            val edge = outline[(i + 1) % outline.size] - outline[i]
            BrandVector(edge.y, -edge.x, 0f).unit()
        }
        val outward = outline.indices.map { i -> (normals[(i + normals.size - 1) % normals.size] + normals[i]).unit() }
        val inset = outline.indices.map { i ->
            outline[i] - outward[i] * (BEVEL / outward[i].dot(normals[i]).coerceAtLeast(.5f))
        }
        fun ring(points: List<BrandVector>, z: Float) = points.map { it.copy(z = z) }
        val front = ring(inset, HALF_DEPTH)
        val frontEdge = ring(outline, HALF_DEPTH - BEVEL)
        val backEdge = ring(outline, -HALF_DEPTH + BEVEL)
        val back = ring(inset, -HALF_DEPTH)
        val result = mutableListOf<BrandMeshFace>()
        for (triangle in triangulate(inset)) {
            for ((ring, normal) in listOf(front to BrandVector(0f, 0f, 1f), back to BrandVector(0f, 0f, -1f))) {
                result += BrandMeshFace(triangle.map { BrandMeshCorner(ring[it], normal) })
            }
        }
        for (i in outline.indices) {
            val next = (i + 1) % outline.size
            fun strip(a: List<BrandVector>, b: List<BrandVector>, normal: (Int) -> BrandVector) {
                result += BrandMeshFace(listOf(
                    BrandMeshCorner(a[i], normal(i)), BrandMeshCorner(a[next], normal(next)),
                    BrandMeshCorner(b[next], normal(next)), BrandMeshCorner(b[i], normal(i)),
                ))
            }
            strip(front, frontEdge) { (outward[it] + BrandVector(0f, 0f, 1f)).unit() }
            strip(frontEdge, backEdge) { outward[it] }
            strip(backEdge, back) { (outward[it] + BrandVector(0f, 0f, -1f)).unit() }
        }
        return result
    }

    /** Ear clipping preserves the concave diagonal cuts; a triangle fan would fill them in. */
    private fun triangulate(points: List<BrandVector>): List<List<Int>> {
        val remaining = points.indices.toMutableList()
        val triangles = mutableListOf<List<Int>>()
        while (remaining.size > 3) {
            val ear = remaining.indices.firstOrNull { i ->
                val a = remaining[(i + remaining.size - 1) % remaining.size]
                val b = remaining[i]
                val c = remaining[(i + 1) % remaining.size]
                cross(points[a], points[b], points[c]) > .0001f && remaining.none { candidate ->
                    candidate != a && candidate != b && candidate != c &&
                        inside(points[candidate], points[a], points[b], points[c])
                }
            } ?: error("Canonical Inzira outline cannot be triangulated")
            triangles += listOf(remaining[(ear + remaining.size - 1) % remaining.size], remaining[ear], remaining[(ear + 1) % remaining.size])
            remaining.removeAt(ear)
        }
        triangles += remaining.toList()
        return triangles
    }

    private fun cross(a: BrandVector, b: BrandVector, c: BrandVector) =
        (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun inside(p: BrandVector, a: BrandVector, b: BrandVector, c: BrandVector) =
        cross(a, b, p) >= -.0001f && cross(b, c, p) >= -.0001f && cross(c, a, p) >= -.0001f
}
