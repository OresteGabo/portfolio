package dev.orestegabo.portfolio.investments

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Original vector contours from branding/struct-capital/struct-capital-logo-transparent.svg.
private object IntroContours {
    private fun path(data: String) = PathParser().parsePathString(data).toPath()
    val s = path("M565 312 L425 392 L548 463 Q563 472 563 489 V560 Q563 577 548 586 L516 598 Q503 605 490 598 L375 532 Q361 524 361 508 V456 L502 537 V511 L376 439 Q361 431 361 415 V373 Q361 358 375 350 L489 284 Q502 276 515 284 Z")
    val c = path("M566 326 Q579 318 592 326 L635 351 Q649 359 649 375 V423 L582 385 Q574 381 567 386 Q563 389 563 395 V459 L502 424 V375 Q502 359 516 351 Z M575 500 L649 458 V508 Q649 524 635 532 L575 567 Z")
    val frame = path("M488 222 Q503 213 518 222 L684 318 Q699 327 699 344 V536 Q699 553 684 562 L518 658 Q503 667 488 658 L324 562 Q309 553 309 536 V344 Q309 327 324 318 Z")
}

/** Content arrives as the signature travels into the real welcome artwork. */
internal fun brandWelcomeReveal(progress: Float): Float = brandPhase(progress, .68f, .98f)

private fun brandPhase(progress: Float, start: Float, end: Float): Float =
    FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

/** Uses the same 512-unit viewport as StructMark for an exact vector-to-vector handoff. */
@Composable
internal fun BrandIntroOverlay(progress: Float, destination: Rect?) {
    val ink = MaterialTheme.colorScheme.primary
    val sReveal = brandPhase(progress, .04f, .34f)
    val cReveal = brandPhase(progress, .12f, .40f)
    val outline = brandPhase(progress, .26f, .55f)
    val wordmark = brandPhase(progress, .40f, .58f) * (1f - brandPhase(progress, .65f, .83f))
    val travel = brandPhase(progress, .65f, 1f)
    val guides = brandPhase(progress, 0f, .10f) * (1f - brandPhase(progress, .31f, .48f)) * .14f
    val frameMeasure = remember { PathMeasure().apply { setPath(IntroContours.frame, false) } }
    val tracedFrame = remember { Path() }
    Box(Modifier.fillMaxSize().clearAndSetSemantics { contentDescription = "Struct Capital" }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) awaitPointerEvent().changes.forEach { it.consume() }
            }
        }) {
        Canvas(Modifier.fillMaxSize()) {
            val startingSide = minOf(244.dp.toPx(), size.width * .64f, size.height * .40f)
            val startingTop = (size.height - startingSide) / 2f - 28.dp.toPx()
            val initial = Rect((size.width - startingSide) / 2f, startingTop,
                (size.width + startingSide) / 2f, startingTop + startingSide)
            val target = destination ?: initial
            val left = initial.left + (target.left - initial.left) * travel
            val top = initial.top + (target.top - initial.top) * travel
            val side = initial.width + (target.width - initial.width) * travel
            translate(left, top) {
                scale(side / 512f, side / 512f, pivot = Offset.Zero) {
                    translate(-248f, -183f) {
                        if (guides > 0f) {
                            drawLine(ink, Offset(300f, 560f), Offset(700f, 329f), 1.3f, alpha = guides)
                            drawLine(ink, Offset(300f, 329f), Offset(700f, 560f), 1.3f, alpha = guides)
                            drawLine(ink, Offset(503f, 210f), Offset(503f, 670f), 1.3f, alpha = guides)
                        }
                        // Reveal the original ribbons within their geometry, with a staggered cadence.
                        clipRect(left = 350f, top = 270f, right = 350f + 220f * sReveal, bottom = 610f) {
                            drawPath(IntroContours.s, ink)
                        }
                        clipRect(left = 490f, top = 580f - 270f * cReveal, right = 660f, bottom = 580f) {
                            drawPath(IntroContours.c, ink)
                        }
                        if (outline > 0f) {
                            tracedFrame.reset()
                            frameMeasure.getSegment(0f, frameMeasure.length * outline, tracedFrame, true)
                            drawPath(tracedFrame, ink, style = Stroke(8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val startingSide = minOf(244.dp, maxWidth * .64f, maxHeight * .40f)
            Text("STRUCT CAPITAL", color = ink, fontSize = 16.sp, letterSpacing = 3.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopCenter)
                    .offset(y = (maxHeight + startingSide) / 2f - 12.dp)
                    .graphicsLayer {
                        alpha = wordmark
                        translationY = 5.dp.toPx() * (1f - brandPhase(progress, .40f, .58f))
                    })
        }
    }
}
