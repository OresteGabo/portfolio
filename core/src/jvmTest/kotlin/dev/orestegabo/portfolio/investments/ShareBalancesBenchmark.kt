package dev.orestegabo.portfolio.investments

import java.io.File
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import com.sun.management.ThreadMXBean

@Volatile private var balanceBenchmarkBlackhole: Any? = null

/** Opt-in local JVM experiment, not a timing assertion in the functional test suite. */
fun main(args: Array<String>) {
    val output = File(args[0])
    val label = args.getOrNull(1) ?: "current"
    val bean = (ManagementFactory.getThreadMXBean() as? ThreadMXBean)?.takeIf { it.isThreadAllocatedMemorySupported }
        ?.apply { isThreadAllocatedMemoryEnabled = true }
    val thread = Thread.currentThread().id
    fun allocated(): Long? = bean?.getThreadAllocatedBytes(thread)
    fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    fun percentile(values: List<Long>, fraction: Double) = values.sorted()[kotlin.math.ceil(values.size * fraction).toInt().coerceAtLeast(1) - 1]
    val fixtures = listOf("small" to shareBalanceFixture(20), "medium" to shareBalanceFixture(200),
        "large" to shareBalanceFixture(1000), "mostly_unmatched" to shareBalanceFixture(20, 4900))
    repeat(10) { balanceBenchmarkBlackhole = shareBalances(fixtures[2].second.holdings, fixtures[2].second.entries) }
    val cases = fixtures.map { (name, fixture) ->
        repeat(10) { balanceBenchmarkBlackhole = shareBalances(fixture.holdings, fixture.entries) }
        val times = mutableListOf<Long>()
        val allocations = mutableListOf<Long>()
        repeat(15) {
            val memory = allocated()
            val start = System.nanoTime()
            val rows = shareBalances(fixture.holdings, fixture.entries)
            val elapsed = System.nanoTime() - start
            val memoryAfter = allocated()
            times += elapsed
            if (memory != null && memoryAfter != null) allocations += memoryAfter - memory
            balanceBenchmarkBlackhole = rows
        }
        val rows = shareBalances(fixture.holdings, fixture.entries)
        JsonRecord().put("name", name).put("holdings", fixture.holdings.size).put("entries", fixture.entries.size)
            .put("samples_ns", JsonList(times)).put("median_ns", percentile(times, .5)).put("p90_ns", percentile(times, .9))
            .put("samples_allocated_bytes", JsonList(allocations))
            .put("median_allocated_bytes", allocations.takeIf { it.isNotEmpty() }?.let { percentile(it, .5) })
            .put("input_sha256", hash((JsonList(fixture.holdings).toString() + JsonList(fixture.entries)).encodeToByteArray()))
            .put("output_sha256", hash(JsonList(rows).toString().encodeToByteArray()))
    }
    val source = File("core/src/commonMain/kotlin/dev/orestegabo/portfolio/investments/InvestmentLedger.kt")
    val result = JsonRecord().put("label", label).put("java", System.getProperty("java.runtime.version"))
        .put("vm", System.getProperty("java.vm.name")).put("os", System.getProperty("os.name"))
        .put("arch", System.getProperty("os.arch")).put("available_processors", Runtime.getRuntime().availableProcessors())
        .put("heap_bytes", Runtime.getRuntime().maxMemory()).put("warmup_per_case", 10).put("samples_per_case", 15)
        .put("implementation_sha256", hash(source.readBytes())).put("cases", JsonList(cases))
    output.parentFile.mkdirs(); output.writeText(result.toString() + "\n")
    for (row in cases) println("shareBalances ${row.text("name")}: median_ns=${row.text("median_ns")} allocated_bytes=${row.text("median_allocated_bytes")}")
}
