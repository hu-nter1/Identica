package com.example.identica.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.identica.biometric.HashUtil
import com.example.identica.biometric.PhotoVisualSignature
import com.example.identica.biometric.FingerprintTemplateEngine
import com.example.identica.biometric.SimilarityCalculator
import com.example.identica.data.IdenticaRepository
import com.example.identica.data.MatchType
import com.example.identica.data.local.entity.RegisteredPerson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ScanOutcome(
    val person: RegisteredPerson?,
    val percent: Int,
    val matchType: String,
    val isMatch: Boolean
)

data class SearchUiState(
    val mode: String = MatchType.PHOTO,
    val probeReady: Boolean = false,
    val probeLabel: String = "NO PROBE CAPTURED",
    val scanning: Boolean = false,
    val sweepProgress: Int = 0,
    val message: String = "STANDBY",
    val liveMetric: Int = 0,
    val outcome: ScanOutcome? = null,
    val error: String? = null
)

class SearchViewModel(
    private val repository: IdenticaRepository,
    private val fingerprintEngine: FingerprintTemplateEngine = FingerprintTemplateEngine()
) : ViewModel() {

    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()

    private var probeHash: String? = null
    private var scanJob: Job? = null

    fun selectMode(mode: String) {
        if (mutableState.value.scanning) return
        probeHash = null
        mutableState.value = SearchUiState(mode = mode)
    }

    fun onPhotoProbe(file: File) {
        viewModelScope.launch {
            val hash = withContext(Dispatchers.IO) { PhotoVisualSignature.fromFile(file) }
                ?: run { mutableState.update { it.copy(error = "Could not read this photo. Try another image.") }; return@launch }
            probeHash = hash
            markProbeReady("PHOTO READY • VISUAL SIGNATURE ${hash.takeLast(8).uppercase()}")
        }
    }

    fun onFingerprintFile(file: File) {
        viewModelScope.launch {
            probeHash = withContext(Dispatchers.IO) { HashUtil.sha256Hex(file.readBytes()) }
            markProbeReady("FINGERPRINT FILE READY")
        }
    }

    fun fingerprintVerified() {
        if (mutableState.value.scanning) return
        viewModelScope.launch {
            val enrolled = repository.getPersons().mapNotNull { it.fingerprintHash?.takeIf(String::isNotBlank) }
            probeHash = fingerprintEngine.createProbe(enrolled)
            markProbeReady("FINGERPRINT VERIFIED")
        }
    }

    fun startScan() {
        val probe = probeHash ?: return
        if (mutableState.value.scanning) return
        val mode = mutableState.value.mode

        scanJob = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    scanning = true,
                    outcome = null,
                    error = null,
                    sweepProgress = 0,
                    liveMetric = 0,
                    message = "INITIALIZING SENSOR ARRAY"
                )
            }
            delay(INIT_DELAY_MS)

            val persons = repository.getPersons()
            if (persons.isEmpty()) {
                mutableState.update {
                    it.copy(scanning = false, error = "No registered profiles to search.", message = "STANDBY")
                }
                return@launch
            }

            var bestPerson: RegisteredPerson? = null
            var bestScore = 0
            for ((index, person) in persons.withIndex()) {
                delay(PER_PROFILE_DELAY_MS)
                val reference = if (mode == MatchType.FINGERPRINT) {
                    person.fingerprintHash
                } else {
                    // Recompute from the stored photo so older records using SHA-256 still work.
                    withContext(Dispatchers.IO) { PhotoVisualSignature.fromFile(File(person.photoPath)) }
                }
                if (reference.isNullOrBlank()) continue
                val score = SimilarityCalculator.percent(probe, reference)
                if (score > bestScore) {
                    bestScore = score
                    bestPerson = person
                }
                val done = index + 1
                val currentBest = bestScore
                mutableState.update {
                    it.copy(
                        sweepProgress = done * 100 / persons.size,
                        liveMetric = currentBest,
                        message = "COMPARING PROFILE $done/${persons.size}"
                    )
                }
            }

            val matched = bestScore >= MATCH_THRESHOLD_PERCENT
            val matchedPerson = if (matched) bestPerson else null
            repository.recordSearch(matchedPerson, mode, bestScore)
            delay(FINALIZE_DELAY_MS)

            mutableState.update {
                it.copy(
                    scanning = false,
                    sweepProgress = 100,
                    liveMetric = bestScore,
                    message = if (matched) "MATCH CONFIRMED" else "NO CONFIDENT MATCH",
                    outcome = ScanOutcome(matchedPerson, bestScore, mode, matched)
                )
            }
        }
    }

    private fun markProbeReady(label: String) {
        mutableState.update {
            it.copy(
                probeReady = true,
                probeLabel = label,
                outcome = null,
                error = null,
                liveMetric = 0,
                sweepProgress = 0,
                message = "PROBE READY"
            )
        }
    }

    override fun onCleared() {
        scanJob?.cancel()
        super.onCleared()
    }

    companion object {
        const val MATCH_THRESHOLD_PERCENT = 85
        private const val INIT_DELAY_MS = 700L
        private const val PER_PROFILE_DELAY_MS = 450L
        private const val FINALIZE_DELAY_MS = 500L

        fun factory(repository: IdenticaRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(repository) as T
            }
    }
}
