package dev.orestegabo.portfolio.investments

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Serializes local guest operations; the session owner supplies the current mode/revision gates. */
internal class PortfolioGuestWorkspace(
    private val scope: CoroutineScope,
    private val guestRepository: GuestPortfolioRepository?,
    importWorkDispatcher: CoroutineDispatcher,
    private val currentGeneration: () -> Int,
    private val isForeground: () -> Boolean,
    private val personalMode: () -> Boolean,
) {
    private val generation get() = currentGeneration()
    private val foreground get() = isForeground()
    private val isPersonal get() = personalMode()
    var guestPortfolio by mutableStateOf<GuestPortfolio?>(null); private set
    var guestStorageLoading by mutableStateOf(false); private set
    var guestStorageError by mutableStateOf<String?>(null); private set
    private var guestJob: Job? = null
    val guestImport = guestRepository?.let { GuestPdfImport(it, importWorkDispatcher) }
    fun action(operation: suspend () -> GuestPortfolio) {
        if (!foreground || !isPersonal || guestJob?.isActive == true || guestRepository == null) return
        val revision = generation
        guestStorageLoading = true; guestStorageError = null
        guestJob = scope.launch {
            try {
                val result = operation()
                if (revision == generation && foreground && isPersonal) guestPortfolio = result
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (revision == generation) guestStorageError = if (failure is GuestPdfReviewException || failure is GuestDataDeletionException) failure.message else "Local portfolio could not be opened or saved. Your existing data was not reset. Unlock your device and retry."
            } finally { if (revision == generation) { guestStorageLoading = false; guestJob = null } }
        }
    }
    fun write(operation: suspend () -> GuestPortfolio, completed: () -> Unit = {}) {
        val revision = generation
        action { operation().also { if (revision == generation && isPersonal) completed() } }
    }
    fun dismissError() { guestStorageError = null }
    fun dropPortfolio() { guestPortfolio = null }
    fun cancelOperation() { guestJob?.cancel() }
    fun reset() {
        guestJob?.cancel(); guestJob = null
        guestStorageLoading = false; guestPortfolio = null; guestStorageError = null
        guestImport?.clear()
    }
}
