package dev.orestegabo.portfolio.investments

import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Run on an isolated emulator with connectivity disabled. All PDFs are synthetic vector fixtures. */
class GuestPdfNativeTest {
    @Test fun privateEngineSelectReviewSavePreviewAndDuplicateWorkWithoutNetwork() = runBlocking {
        val i=InstrumentationRegistry.getInstrumentation()
        val context=i.targetContext
        val name="test-p4-${UUID.randomUUID()}"
        val store=AndroidGuestSnapshotStorage(context,name)
        val repository=LocalGuestPortfolioRepository(store,documentStorage=AndroidGuestDocumentStorage(context))
        val workflow=GuestPdfImport(repository)
        val scenario=ActivityScenario.launch<EngineHostActivity>(Intent(context,EngineHostActivity::class.java).putExtra("privateDocuments",true))
        lateinit var engine:LocalInvestmentEngine
        scenario.onActivity { engine=it.engine; assertTrue(engine.view.settings.blockNetworkLoads) }
        fun pdf(asset:String, scanned:Boolean=false):ByteArray {
            val text=i.context.assets.open(asset).bufferedReader().use { it.readText() }
            val document=PdfDocument()
            try {
                val page=document.startPage(PdfDocument.PageInfo.Builder(1100,1800,1).create())
                val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=15f }
                val bitmap = if (scanned) android.graphics.Bitmap.createBitmap(1100,1800,android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) } else null
                val canvas = bitmap?.let { android.graphics.Canvas(it) } ?: page.canvas
                val lines=text.lines().flatMap { line -> line.split(' ').fold(mutableListOf("")) { rows,word ->
                    if(rows.last().length+word.length>100)rows.add(word) else rows[rows.lastIndex]=(rows.last()+" "+word).trim(); rows
                } }
                lines.forEachIndexed { index,line -> canvas.drawText(line,30f,35f+index*24f,paint) }
                if (bitmap != null) { page.canvas.drawBitmap(bitmap,0f,0f,null); bitmap.recycle() }
                document.finishPage(page)
                return ByteArrayOutputStream().also(document::writeTo).toByteArray()
            } finally { document.close() }
        }
        fun actions(bytes:ByteArray)=object:NativeActions {
            override val pickedPdfName="sanitized.pdf"
            override suspend fun pickPdf(onSelected:()->Unit):ByteArray?=error("Use the private picker")
            override suspend fun pickPrivatePdf(onSelected:()->Unit):ByteArray { onSelected(); return bytes.copyOf() }
            override suspend fun savePdf(fileName:String,bytes:ByteArray)=error("No export")
            override fun openUrl(url:String)=error("No network")
            override fun shareText(text:String)=error("No sharing")
        }
        val parser=object:InvestmentDocumentParser {
            override var onProgress:(String)->Unit
                get()=engine.onProgress
                set(value){engine.onProgress=value}
            override suspend fun parse(pdf:ByteArray): JsonRecord {
                val start=android.os.SystemClock.elapsedRealtimeNanos()
                val result=engine.run("pdf",pdf=pdf)
                android.util.Log.i("GuestReleaseBenchmark","pdf_parse type=${result.text("kind")} ocr=${result.optBoolean("usedOcr")} micros=${(android.os.SystemClock.elapsedRealtimeNanos()-start)/1000}")
                return result
            }
            override fun cancel()=engine.cancel()
        }
        try {
            for ((asset,kind) in listOf("equity-purchase.txt" to "equity","bnr-personal-synthetic.txt" to "bond")) {
                val original=pdf(asset)
                workflow.select(actions(original),parser)
                assertNull(workflow.failure); assertEquals(kind,workflow.review?.text("kind"))
                val before=repository.load().portfolio.documents.size
                assertEquals(before,repository.load().portfolio.documents.size)
                val body=requireNotNull(workflow.fields())
                assertTrue(runCatching { workflow.save(body) }.isFailure)
                workflow.confirmed=true
                val saved=workflow.save(body)
                assertEquals(before+1,saved.portfolio.documents.size)
                val doc=saved.portfolio.documents.last()
                val bytes=repository.documentBytes(doc.text("id"))
                assertArrayEquals(original,bytes)
                val renderer=AndroidPdfDocument(bytes,context.cacheDir)
                try {
                    assertTrue(renderer.pageCount>0); renderer.renderPage(0,400)
                    val previews=java.io.File(context.cacheDir,"pdf-preview").listFiles().orEmpty()
                    assertTrue(previews.none { it.extension=="pdf" })
                } finally { renderer.close(); bytes.fill(0) }
                workflow.select(actions(original),parser)
                assertNotNull(workflow.duplicate); assertNull(workflow.review)
                assertEquals(before+1,repository.load().portfolio.documents.size)
            }
            val beforeScan = repository.load().portfolio.documents.size
            val scanned = pdf("equity-purchase.txt", scanned=true)
            workflow.select(actions(scanned),parser)
            assertNull(workflow.failure); assertEquals("equity",workflow.review?.text("kind")); assertTrue(requireNotNull(workflow.review).optBoolean("usedOcr"))
            assertEquals(beforeScan,repository.load().portfolio.documents.size); workflow.clear()
            workflow.select(actions("%PDF-1.7 damaged\n%%EOF".toByteArray()),parser)
            assertNotNull(workflow.failure); assertNull(workflow.review)
        } finally {
            workflow.clear()
            repository.load().portfolio.documents.forEach { repository.deleteDocument(it.text("id")) }
            store.delete(); scenario.close()
        }
    }
}
