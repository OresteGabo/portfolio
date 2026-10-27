package dev.orestegabo.portfolio.investments

/** Small, shared vector PDF renderer. All layout and pagination are identical on Android and iOS. */
internal class ReportPdf(private val title: String, private val footer: String, private val french: Boolean = false) {
    private val pages = mutableListOf<StringBuilder>()
    private val content get() = pages.last()
    var y = 0.0
        private set
    private val ink = "0.13 0.16 0.14"
    private val muted = "0.38 0.42 0.39"
    private val gold = "0.43 0.37 0.06"
    private val cream = "0.98 0.97 0.94"
    private var section = 0
    init { newPage() }
    fun newPage() {
        pages += StringBuilder()
        rect(0.0, 0.0, 595.0, 4.0, gold)
        logo(36.0, 23.0, 42.0)
        text("STRUCT CAPITAL", 91.0, 42.0, 17.0, true)
        text(title.removePrefix("STRUCT CAPITAL / "), 91.0, 58.0, 8.0, color = muted)
        rect(36.0, 80.0, 523.0, 0.6, "0.85 0.84 0.77")
        y = 113.0
    }
    fun ensure(height: Double) { if (y + height > 780) newPage() }
    fun gap(height: Double = 12.0) { y += height }
    fun heading(value: String, large: Boolean = false) {
        ensure(120.0)
        if (large) {
            section++
            roundedRect(36.0, y - 21, 31.0, 31.0, 8.0, cream)
            text(section.toString().padStart(2, '0'), 42.0, y, 11.0, true, gold)
        }
        val x = if (large) 80.0 else 36.0
        val size = if (large) 24.0 else 17.0
        wrap(value, 559 - x, size, true).forEach { line ->
            text(line, x, y, size, true)
            y += if (large) 31 else 24
        }
        gap(if (large) 6.0 else 5.0)
    }
    fun paragraph(value: String, size: Double = 10.0) {
        wrap(value, 523.0, size).forEach { line ->
            ensure(size + 7)
            text(line, 36.0, y, size, color = muted)
            y += size + 5
        }
        gap(9.0)
    }
    fun banner(label: String, value: String) {
        val lines = wrap(value, 475.0, 30.0, true)
        val height = 50.0 + lines.size * 35
        ensure(height + 20)
        roundedRect(36.0, y - 8, 523.0, height, 14.0, "0.14 0.19 0.16")
        // Restrained diamond linework echoes the Rwandan pattern used in the app's hero.
        content.append("q\n")
        roundedPath(36.0, y - 8, 523.0, height, 14.0)
        content.append("W n 0.28 0.31 0.20 RG 0.65 w\n")
        for (center in listOf(438.0, 493.0, 548.0)) for (radius in listOf(16.0, 25.0, 34.0, 43.0)) {
            val cy = 842 - y - 36
            content.append("${n(center)} ${n(cy + radius)} m ${n(center + radius)} ${n(cy)} l ${n(center)} ${n(cy - radius)} l ${n(center - radius)} ${n(cy)} l h S\n")
        }
        content.append("Q\n")
        text(label, 54.0, y + 14, 10.0, color = "0.83 0.79 0.56")
        lines.forEachIndexed { index, line -> text(line, 54.0, y + 49 + index * 35, 30.0, true, "1 1 1") }
        y += height + 8
    }
    fun summaryMetrics(metrics: List<ReportMetric>) {
        metrics.chunked(2).forEach { row ->
            val cellWidth = if (row.size == 1) 523.0 else 255.5
            val sizes = row.map { metric -> fit(metric.value, cellWidth - 32, 17.0, 12.0) }
            val labels = row.map { wrap(it.label, cellWidth - 32, 9.0) }
            val values = row.mapIndexed { index, metric -> wrap(metric.value, cellWidth - 32, sizes[index], true) }
            val height = row.indices.maxOf { 27.0 + labels[it].size * 12 + values[it].size * 21 }
            ensure(height + 12)
            row.forEachIndexed { index, metric ->
                val x = 36 + index * (cellWidth + 12)
                roundedRect(x, y - 9, cellWidth, height, 10.0, cream)
                labels[index].forEachIndexed { line, label -> text(label, x + 16, y + 8 + line * 12, 9.0, color = muted) }
                values[index].forEachIndexed { line, value -> text(value, x + 16, y + 18 + labels[index].size * 12 + line * 21, sizes[index], true, gainColor(metric.gain)) }
            }
            y += height + 12
        }
    }
    fun allocation(label: String, bondLabel: String, shareLabel: String, bonds: Double, shares: Double) {
        if (!bonds.isFinite() || !shares.isFinite() || bonds + shares <= 0) return
        ensure(78.0)
        text(label, 36.0, y, 10.0, true)
        val fraction = (bonds / (bonds + shares)).coerceIn(0.0, 1.0)
        roundedRect(36.0, y + 13, 523.0, 9.0, 4.5, "0.25 0.38 0.30")
        if (fraction > 0) roundedRect(36.0, y + 13, 523.0 * fraction, 9.0, 4.5, "0.65 0.55 0.22")
        val bondTenths = kotlin.math.round(fraction * 1000).toInt()
        fun percentage(tenths: Int) = (tenths / 10.0).toString().let { if (french) it.replace('.', ',') + " %" else "$it%" }
        text("$bondLabel  ${percentage(bondTenths)}", 36.0, y + 42, 9.0, color = muted)
        val right = "$shareLabel  ${percentage(1000 - bondTenths)}"
        text(right, 559 - width(right, 9.0), y + 42, 9.0, color = muted)
        y += 65
    }
    fun metric(label: String, value: String, gain: Double? = null) {
        val labels = wrap(label, 253.0, 9.5)
        val values = wrap(value, 252.0, 10.5, true)
        val height = maxOf(labels.size, values.size) * 14.0 + 12
        ensure(height)
        labels.forEachIndexed { index, line -> text(line, 36.0, y + index * 14, 9.5, color = muted) }
        values.forEachIndexed { index, line -> text(line, 559 - width(line, 10.5, true), y + index * 14, 10.5, true, gainColor(gain)) }
        y += height
    }
    fun record(title: String, details: List<Pair<String, String>>, highlightLast: Double? = null) {
        val facts = details.filter { it.second.isNotBlank() }
        val titleLines = wrap(title, 487.0, 12.0, true)
        val factRows = facts.chunked(2)
        val rowHeights = factRows.map { row -> row.maxOf { pair ->
            8.0 + wrap(pair.first, 230.0, 8.5).size * 11 + wrap(pair.second, 230.0, 10.5, true).size * 14
        } }
        val height = 24 + titleLines.size * 16 + rowHeights.sum() + 6
        // Pathological, very long API text can span pages without clipping a single huge card.
        if (height > 655) {
            heading(title)
            facts.forEachIndexed { index, fact -> metric(fact.first, fact.second, if (index == facts.lastIndex) highlightLast else null) }
            gap(); return
        }
        ensure(height + 15)
        val top = y - 13
        roundedRect(36.0, top, 523.0, height, 11.0, "0.975 0.977 0.965")
        roundedRect(36.0, top, 4.0, height, 2.0, "0.68 0.60 0.30")
        titleLines.forEachIndexed { index, line -> text(line, 54.0, y + 4 + index * 16, 12.0, true) }
        var baseline = y + titleLines.size * 16 + 19
        factRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { index, (label, value) ->
                val x = 54 + index * 255.5
                val labels = wrap(label, 230.0, 8.5)
                labels.forEachIndexed { line, string -> text(string, x, baseline + line * 11, 8.5, color = muted) }
                val last = rowIndex * 2 + index == facts.lastIndex
                wrap(value, 230.0, 10.5, true).forEachIndexed { line, string ->
                    text(string, x, baseline + labels.size * 11 + 5 + line * 14, 10.5, true, gainColor(if (last) highlightLast else null))
                }
            }
            baseline += rowHeights[rowIndex]
        }
        y = top + height + 22
    }
    fun table(headers: List<String>, rows: List<List<String>>, widths: List<Double>) {
        require(headers.size == widths.size && widths.sum() <= 523.1)
        ensure(85.0)
        rowHeader(headers, widths)
        rows.forEachIndexed { rowIndex, cells ->
            val lines = cells.mapIndexed { index, value -> wrap(value, widths[index] - 16, 9.0) }
            val height = lines.maxOf { it.size }.coerceAtLeast(1) * 13.0 + 15
            if (y + height > 780) { newPage(); rowHeader(headers, widths) }
            if (rowIndex % 2 == 0) rect(36.0, y - 11, 523.0, height, "0.965 0.968 0.951")
            var x = 36.0
            lines.forEachIndexed { index, cell ->
                cell.forEachIndexed { line, value ->
                    val tx = if (index == 0) x + 8 else x + widths[index] - 8 - width(value, 9.0)
                    text(value, tx, y + line * 13, 9.0)
                }
                x += widths[index]
            }
            y += height
        }
        gap(20.0)
    }
    private fun rowHeader(headers: List<String>, widths: List<Double>) {
        val height = headers.mapIndexed { index, h -> wrap(h, widths[index] - 16, 8.0, true).size }.max() * 12.0 + 16
        roundedRect(36.0, y - 12, 523.0, height, 5.0, "0.18 0.23 0.19")
        var x = 36.0
        headers.forEachIndexed { index, header ->
            wrap(header, widths[index] - 16, 8.0, true).forEachIndexed { line, value ->
                val tx = if (index == 0) x + 8 else x + widths[index] - 8 - width(value, 8.0, true)
                text(value, tx, y + line * 12, 8.0, true, "1 1 1")
            }
            x += widths[index]
        }
        y += height
    }
    fun bar(label: String, amount: Double, maximum: Double, value: String) {
        ensure(56.0)
        roundedRect(36.0, y - 13, 42.0, 21.0, 5.0, cream)
        text(label, 43.0, y, 10.0, true, gold)
        val size = fit(value, 465.0, 10.0, 8.0)
        text(value, 559 - width(value, size), y, size)
        roundedRect(36.0, y + 14, 523.0, 7.0, 3.5, "0.93 0.93 0.89")
        if (maximum > 0 && amount > 0) roundedRect(36.0, y + 14, (amount / maximum).coerceIn(0.0, 1.0) * 523, 7.0, 3.5, "0.59 0.48 0.14")
        y += 48
    }
    private fun fit(value: String, maxWidth: Double, initial: Double, min: Double): Double {
        var size = initial
        while (size > min && width(value, size, true) > maxWidth) size -= 0.5
        return size
    }
    private fun gainColor(gain: Double?) = when {
        gain == null || gain == 0.0 -> ink
        gain > 0 -> "0.08 0.43 0.27"
        else -> "0.71 0.18 0.18"
    }
    private fun roundedRect(x: Double, top: Double, w: Double, h: Double, radius: Double, color: String) {
        content.append("$color rg ")
        roundedPath(x, top, w, h, radius)
        content.append("f\n")
    }
    private fun roundedPath(x: Double, top: Double, w: Double, h: Double, radius: Double) {
        val r = radius.coerceAtMost(minOf(w, h) / 2)
        val k = r * 0.55228475
        val b = 842 - top - h
        content.append("${n(x+r)} ${n(b)} m ${n(x+w-r)} ${n(b)} l ${n(x+w-r+k)} ${n(b)} ${n(x+w)} ${n(b+r-k)} ${n(x+w)} ${n(b+r)} c ")
        content.append("${n(x+w)} ${n(b+h-r)} l ${n(x+w)} ${n(b+h-r+k)} ${n(x+w-r+k)} ${n(b+h)} ${n(x+w-r)} ${n(b+h)} c ")
        content.append("${n(x+r)} ${n(b+h)} l ${n(x+r-k)} ${n(b+h)} ${n(x)} ${n(b+h-r+k)} ${n(x)} ${n(b+h-r)} c ")
        content.append("${n(x)} ${n(b+r)} l ${n(x)} ${n(b+r-k)} ${n(x+r-k)} ${n(b)} ${n(x+r)} ${n(b)} c h ")
    }
    /** Exact Struct Capital source paths; SVG quadratic curves converted to PDF cubic curves. */
    private fun logo(x: Double, top: Double, size: Double) {
        content.append("q ${n(size/512)} 0 0 ${n(-size/512)} ${n(x)} ${n(842-top)} cm 1 0 0 1 -248 -183 cm\n")
        content.append("0.42745 0.36863 0.05882 rg 0.42745 0.36863 0.05882 RG 8 w 1 j\n")
        content.append("488 222 m 498.00000 216.00000 508.00000 216.00000 518 222 c 684 318 l 694.00000 324.00000 699.00000 332.66667 699 344 c 699 536 l 699.00000 547.33333 694.00000 556.00000 684 562 c 518 658 l 508.00000 664.00000 498.00000 664.00000 488 658 c 324 562 l 314.00000 556.00000 309.00000 547.33333 309 536 c 309 344 l 309.00000 332.66667 314.00000 324.00000 324 318 c h S\n")
        content.append("565 312 m 425 392 l 548 463 l 558.00000 469.00000 563.00000 477.66667 563 489 c 563 560 l 563.00000 571.33333 558.00000 580.00000 548 586 c 516 598 l 507.33333 602.66667 498.66667 602.66667 490 598 c 375 532 l 365.66667 526.66667 361.00000 518.66667 361 508 c 361 456 l 502 537 l 502 511 l 376 439 l 366.00000 433.66667 361.00000 425.66667 361 415 c 361 373 l 361.00000 363.00000 365.66667 355.33333 375 350 c 489 284 l 497.66667 278.66667 506.33333 278.66667 515 284 c h f\n")
        content.append("566 326 m 574.66667 320.66667 583.33333 320.66667 592 326 c 635 351 l 644.33333 356.33333 649.00000 364.33333 649 375 c 649 423 l 582 385 l 576.66667 382.33333 571.66667 382.66667 567 386 c 564.33333 388.00000 563.00000 391.00000 563 395 c 563 459 l 502 424 l 502 375 l 502.00000 364.33333 506.66667 356.33333 516 351 c h f\n")
        content.append("575 500 m 649 458 l 649 508 l 649.00000 518.66667 644.33333 526.66667 635 532 c 575 567 l h f\n")
        content.append("Q\n")
    }
    private fun rule() {
        content.append("0.87 0.88 0.84 RG 0.4 w 36 ${n(842 - y + 7)} m 559 ${n(842 - y + 7)} l S\n")
    }
    private fun rect(x: Double, top: Double, w: Double, h: Double, color: String) {
        content.append("$color rg ${n(x)} ${n(842 - top - h)} ${n(w)} ${n(h)} re f\n")
    }
    private fun text(value: String, x: Double, baseline: Double, size: Double, bold: Boolean = false, color: String = ink) {
        content.append("BT /${if (bold) "F2" else "F1"} ${n(size)} Tf $color rg 1 0 0 1 ${n(x)} ${n(842 - baseline)} Tm (${escaped(value)}) Tj ET\n")
    }
    private fun wrap(value: String, max: Double, size: Double, bold: Boolean = false): List<String> {
        val lines = mutableListOf<String>()
        value.split('\n').forEach { paragraph ->
            var line = ""
            paragraph.trim().split(Regex("\\s+")).forEach { word ->
                if (line.isNotEmpty() && width("$line $word", size, bold) > max) { lines += line; line = "" }
                for (char in (if (line.isEmpty()) word else " $word")) {
                    if (line.isNotEmpty() && width(line + char, size, bold) > max) { lines += line; line = "" }
                    line += char
                }
            }
            lines += line
        }
        return lines
    }
    private fun width(value: String, size: Double, bold: Boolean = false) =
        value.sumOf { (if (bold) reportBoldWidths else reportRegularWidths)[ansi(it)] } * size / 1000

    fun finish(attachment: String): ByteArray {
        pages.forEachIndexed { index, page ->
            page.append("0.87 0.88 0.84 RG 0.4 w 36 39 m 559 39 l S\n")
            page.append("BT /F1 8 Tf $muted rg 1 0 0 1 36 25 Tm (${escaped(footer)}) Tj ET\n")
            page.append("0.43 0.37 0.06 rg 511 17 48 17 re f\n")
            page.append("BT /F2 8 Tf 1 1 1 rg 1 0 0 1 520 23 Tm (${index + 1} / ${pages.size}) Tj ET\n")
        }
        val objects = mutableListOf<String>()
        val firstPage = 7
        objects += "<< /Type /Catalog /Pages 2 0 R /Names << /EmbeddedFiles << /Names [(portfolio-data.json) 5 0 R] >> >> >>"
        objects += "<< /Type /Pages /Count ${pages.size} /Kids [${pages.indices.joinToString(" ") { "${firstPage + it * 2} 0 R" }}] >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
        objects += "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
        objects += "<< /Type /Filespec /F (portfolio-data.json) /EF << /F 6 0 R >> >>"
        // ASCII JSON escapes keep object offsets byte-exact and preserve all Unicode data.
        val json = buildString {
            attachment.forEach { if (it.code > 126) append("\\u${it.code.toString(16).padStart(4, '0')}") else append(it) }
        }
        objects += "<< /Type /EmbeddedFile /Subtype /application#2Fjson /Length ${json.length} >>\nstream\n$json\nendstream"
        pages.forEachIndexed { index, stream ->
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${firstPage + index * 2 + 1} 0 R >>"
            objects += "<< /Length ${stream.length} >>\nstream\n$stream\nendstream"
        }
        val output = StringBuilder("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, obj -> output.length.also { output.append("${index + 1} 0 obj\n$obj\nendobj\n") } }
        val start = output.length
        output.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { output.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        output.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$start\n%%EOF\n")
        return output.toString().encodeToByteArray()
    }
    private fun escaped(value: String) = value.map { char ->
        val code = ansi(char)
        when { code == 40 || code == 41 || code == 92 -> "\\${code.toChar()}"; code < 32 || code > 126 -> "\\${code.toString(8).padStart(3, '0')}"; else -> code.toChar().toString() }
    }.joinToString("")
    private fun ansi(char: Char): Int = when (char) {
        '\u202f', '\u00a0' -> 32
        '−', '–', '—' -> 45
        '’', '‘' -> 39
        '“', '”' -> 34
        'œ' -> 156
        'Œ' -> 140
        '…' -> 133
        else -> if (char.code in 32..255) char.code else 63
    }
    private fun n(value: Double) = ((value * 100).toLong() / 100.0).toString()
}

internal data class ReportMetric(val label: String, val value: String, val gain: Double? = null)

private val reportRegularWidths = intArrayOf(761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,278,278,355,556,556,889,667,191,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,278,278,584,584,584,556,1015,667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611,278,278,278,469,556,333,556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500,334,260,334,584,761,556,761,222,556,333,1000,556,556,333,1000,667,333,1000,761,611,761,761,222,222,333,333,350,556,1000,333,1000,500,333,944,761,500,667,278,333,556,556,556,556,260,556,333,737,370,556,584,333,737,333,400,584,333,333,333,556,537,278,333,333,365,556,834,834,834,611,667,667,667,667,667,667,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,500,556,556,556,556,278,278,278,278,556,556,556,556,556,556,556,584,611,556,556,556,556,500,556,500)

private val reportBoldWidths = intArrayOf(761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,761,278,333,474,556,556,889,722,238,333,333,389,584,278,333,278,278,556,556,556,556,556,556,556,556,556,556,333,333,584,584,584,611,975,722,722,722,722,667,611,778,722,278,556,722,611,833,722,778,667,778,722,667,611,722,667,944,667,667,611,333,278,333,584,556,333,556,611,556,611,556,333,611,611,278,278,556,278,889,611,611,611,611,389,556,333,611,556,778,556,556,500,389,280,389,584,761,556,761,278,556,500,1000,556,556,333,1000,667,333,1000,761,611,761,761,278,278,500,500,350,556,1000,333,1000,556,333,944,761,500,667,278,333,556,556,556,556,280,556,333,737,370,556,584,333,737,333,400,584,333,333,333,611,556,278,333,333,365,556,834,834,834,611,722,722,722,722,722,722,1000,722,667,667,667,667,278,278,278,278,722,722,778,778,778,778,778,584,778,722,722,722,722,667,667,611,556,556,556,556,556,556,889,556,556,556,556,556,278,278,278,278,611,611,611,611,611,611,611,584,611,611,611,611,611,556,611,556)
