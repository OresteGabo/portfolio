package dev.orestegabo.portfolio.investments

import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InziraInitialMeshTest {
    @Test fun capsRetainBothDiagonalCutsAndCenterOfInitial() {
        val front = InziraInitialMesh.faces.filter { face -> face.corners.all { it.normal.z == 1f } }
        val back = InziraInitialMesh.faces.filter { face -> face.corners.all { it.normal.z == -1f } }
        assertEquals(InziraInitialMesh.outline.size - 2, front.size)
        assertEquals(front.size, back.size)
        fun covered(x: Float, y: Float) = front.any { face ->
            val p = BrandVector(x - 220f, 200f - y, 0f)
            val (a, b, c) = face.corners.map { it.position }
            fun side(u: BrandVector, v: BrandVector) = (v.x - u.x) * (p.y - u.y) - (v.y - u.y) * (p.x - u.x)
            val edges = listOf(side(a, b), side(b, c), side(c, a))
            edges.all { it >= -.001f } || edges.all { it <= .001f }
        }
        assertTrue(covered(220f, 200f), "The shaft must remain solid")
        assertFalse(covered(225f, 139f), "The upper diagonal cut must remain open")
        assertFalse(covered(215f, 260f), "The lower diagonal cut must remain open")
    }

    @Test fun solidStillHasVisibleThicknessAtBothEdgeOnAngles() {
        for (angle in listOf(90f, 270f)) {
            val (cosine, sine) = InziraInitialMesh.rotation(angle)
            val projected = InziraInitialMesh.faces.flatMap { face -> face.corners.map {
                InziraInitialMesh.project(it.position.turn(cosine, sine))
            } }
            val width = projected.maxOf { it.x } - projected.minOf { it.x }
            assertTrue(width in 25f..40f, "Edge-on width must reflect the solid's depth, not collapse to a line: $width")
            assertTrue(projected.all { it.x.isFinite() && it.y.isFinite() })
        }
    }

    @Test fun lightingRespondsToOrientationAndRotationClosesWithoutSeam() {
        val point = BrandVector(0f, 0f, InziraInitialMesh.HALF_DEPTH)
        val normal = BrandVector(0f, 0f, 1f)
        for (dark in listOf(false, true)) {
            val faceOn = initialMetalColor(point, normal, dark)
            val (cosine, sine) = InziraInitialMesh.rotation(65f)
            val turned = initialMetalColor(point.turn(cosine, sine), normal.turn(cosine, sine), dark)
            assertTrue(abs(faceOn.luminance() - turned.luminance()) > .04f, "Lighting must change with the surface normal")
        }
        val sample = InziraInitialMesh.outline.first()
        val (cosine, sine) = InziraInitialMesh.rotation(360f)
        val closed = sample.turn(cosine, sine)
        assertTrue(abs(closed.x - sample.x) < .001f && abs(closed.y - sample.y) < .001f && abs(closed.z - sample.z) < .001f)
        for (face in InziraInitialMesh.faces) for (corner in face.corners) {
            assertTrue(abs(corner.normal.dot(corner.normal) - 1f) < .001f)
            assertTrue(corner.position.x.isFinite() && corner.position.y.isFinite() && corner.position.z.isFinite())
        }
    }
}
