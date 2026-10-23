package dev.orestegabo.portfolio.investments

import kotlin.math.abs
import kotlin.math.min

/** Normalized OCR boxes, with the origin at the bottom-left as in Apple's Vision framework. */
data class OcrTextBlock(val text: String, val left: Double, val bottom: Double, val height: Double)

/** OCR may return whole table columns first. Rebuild physical rows before parsing labels/values. */
fun layoutOcrText(blocks: List<OcrTextBlock>): String {
    data class Line(val center: Double, val height: Double, val blocks: MutableList<OcrTextBlock>)
    val lines = mutableListOf<Line>()
    blocks.filter { it.text.isNotBlank() && it.left.isFinite() && it.bottom.isFinite() && it.height.isFinite() && it.height > 0 }
        .sortedWith(compareByDescending<OcrTextBlock> { it.bottom + it.height / 2 }.thenBy { it.left })
        .forEach { block ->
            val center = block.bottom + block.height / 2
            val line = lines.lastOrNull()
            if (line != null && abs(line.center - center) <= min(line.height, block.height) * .6) line.blocks += block
            else lines += Line(center, block.height, mutableListOf(block))
        }
    return lines.joinToString("\n") { line -> line.blocks.sortedBy { it.left }.joinToString(" ") { it.text } }
}
