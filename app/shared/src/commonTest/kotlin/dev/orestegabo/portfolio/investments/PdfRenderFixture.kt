package dev.orestegabo.portfolio.investments

/** Vector marks reaching near every edge, plus fine stripes to verify zoom draws new detail. */
internal fun pdfRenderFixture(rotation: Int = 0, cropped: Boolean = false): ByteArray {
    val content = buildString {
        append("0.1 0.5 0.2 rg 20 20 360 560 re f\n")
        append("1 1 1 rg 120 240 160 120 re f\n0 0 0 rg\n")
        for (x in 120 until 280 step 2) append("$x 240 1 120 re f\n")
    }
    val objects = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] ${if (cropped) "/CropBox [50 100 350 500]" else ""} /Rotate $rotation /Resources << >> /Contents 4 0 R >>",
        "<< /Length ${content.length} >>\nstream\n${content}endstream"
    )
    val pdf = StringBuilder("%PDF-1.4\n")
    val offsets = objects.mapIndexed { index, value -> pdf.length.also { pdf.append("${index + 1} 0 obj\n$value\nendobj\n") } }
    val xref = pdf.length
    pdf.append("xref\n0 5\n0000000000 65535 f \n")
    offsets.forEach { pdf.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
    pdf.append("trailer\n<< /Size 5 /Root 1 0 R >>\nstartxref\n$xref\n%%EOF")
    return pdf.toString().encodeToByteArray()
}
