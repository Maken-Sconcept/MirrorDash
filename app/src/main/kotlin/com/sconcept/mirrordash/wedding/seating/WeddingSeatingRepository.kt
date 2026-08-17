package com.sconcept.mirrordash.wedding.seating

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class WeddingSeatingState(
    val data: WeddingSeatingData = WeddingSeatingData(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/** Separate, atomic offline storage for seating data; general MirrorDash preferences stay small. */
class WeddingSeatingRepository(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "wedding")
    private val atomicFile = AtomicFile(File(directory, "seating.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(WeddingSeatingState())
    val state: StateFlow<WeddingSeatingState> = _state.asStateFlow()

    init {
        scope.launch { load() }
    }

    suspend fun replace(data: WeddingSeatingData) {
        withContext(Dispatchers.IO) {
            directory.mkdirs()
            var stream: java.io.FileOutputStream? = null
            try {
                stream = atomicFile.startWrite()
                stream.write(json.encodeToString(data).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
                atomicFile.finishWrite(stream)
                _state.value = WeddingSeatingState(data = data, isLoading = false)
            } catch (error: Exception) {
                stream?.let(atomicFile::failWrite)
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = "Seating data could not be saved: ${error.message ?: "storage error"}",
                )
                throw error
            }
        }
    }

    suspend fun clear() = replace(WeddingSeatingData())

    private suspend fun load() {
        withContext(Dispatchers.IO) {
            val file = atomicFile.baseFile
            if (!file.exists()) {
                _state.value = WeddingSeatingState(isLoading = false)
                return@withContext
            }
            _state.value = runCatching {
                val data = json.decodeFromString<WeddingSeatingData>(
                    atomicFile.readFully().toString(Charsets.UTF_8),
                )
                WeddingSeatingState(data = data, isLoading = false)
            }.getOrElse { error ->
                WeddingSeatingState(
                    isLoading = false,
                    errorMessage = "Seating data could not be opened: ${error.message ?: "invalid file"}",
                )
            }
        }
    }
}
