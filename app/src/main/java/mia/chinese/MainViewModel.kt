package mia.chinese

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mia.chinese.data.CatalogRepository
import mia.chinese.data.CatalogValidator
import mia.chinese.data.LastCatalogLocationEntity
import mia.chinese.data.LastResumePointerEntity
import mia.chinese.data.ProgressRepository
import mia.chinese.data.ResumeTargetResolver
import mia.chinese.data.VideoProgressEntity
import mia.chinese.model.Catalog

sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data class Ready(val catalog: Catalog) : CatalogLoadState
    data class Error(val message: String) : CatalogLoadState
}

sealed interface CatalogSyncState {
    data object Idle : CatalogSyncState
    data object Unconfigured : CatalogSyncState
    data object Running : CatalogSyncState
    data class Success(val message: String) : CatalogSyncState
    data class Error(val message: String) : CatalogSyncState
}

class MainViewModel(
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository
) : ViewModel() {
    private val _catalogState = MutableStateFlow<CatalogLoadState>(CatalogLoadState.Loading)
    val catalogState: StateFlow<CatalogLoadState> = _catalogState.asStateFlow()
    private val _syncState = MutableStateFlow<CatalogSyncState>(CatalogSyncState.Idle)
    val syncState: StateFlow<CatalogSyncState> = _syncState.asStateFlow()

    val allProgress: StateFlow<List<VideoProgressEntity>> = progressRepository.allProgress.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val lastResumePointer: StateFlow<LastResumePointerEntity?> =
        progressRepository.lastResumePointer.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val lastCatalogLocation: StateFlow<LastCatalogLocationEntity?> =
        progressRepository.lastCatalogLocation.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    init {
        loadCatalog()
    }

    fun retry() {
        loadCatalog()
    }

    fun syncCatalog() {
        val endpoint = BuildConfig.CATALOG_ENDPOINT.trim()
        val checksum = BuildConfig.CATALOG_SHA256.trim()
        if (endpoint.isBlank() || checksum.isBlank()) {
            _syncState.value = CatalogSyncState.Unconfigured
            return
        }
        _syncState.value = CatalogSyncState.Running
        viewModelScope.launch {
            when (val result = catalogRepository.sync(endpoint, checksum)) {
                is mia.chinese.data.CatalogSyncResult.Updated -> {
                    _catalogState.value = CatalogLoadState.Ready(result.catalog)
                    markStaleResumeIfNeeded(result.catalog)
                    _syncState.value = CatalogSyncState.Success("已套用 ${result.catalog.contentVersion}")
                }
                is mia.chinese.data.CatalogSyncResult.NotModified -> {
                    _catalogState.value = CatalogLoadState.Ready(result.catalog)
                    _syncState.value = CatalogSyncState.Success("課程資料沒有變更")
                }
                is mia.chinese.data.CatalogSyncResult.Rejected -> {
                    _syncState.value = CatalogSyncState.Error(result.reason)
                }
                is mia.chinese.data.CatalogSyncResult.Failed -> {
                    _syncState.value = CatalogSyncState.Error(result.reason)
                }
            }
        }
    }

    private fun loadCatalog() {
        _catalogState.value = CatalogLoadState.Loading
        viewModelScope.launch {
            runCatching { catalogRepository.loadCatalog() }
                .mapCatching { CatalogValidator.requireValid(it) }
                .onSuccess {
                    _catalogState.value = CatalogLoadState.Ready(it)
                    markStaleResumeIfNeeded(it)
                }
                .onFailure {
                    _catalogState.value = CatalogLoadState.Error(
                        it.message ?: "無法載入課程資料"
                    )
                }
        }
    }

    private suspend fun markStaleResumeIfNeeded(catalog: Catalog) {
        val pointer = progressRepository.lastResumePointer.first() ?: return
        val saved = progressRepository.getProgress(pointer.videoId, pointer.revision)
        if (ResumeTargetResolver.resolve(catalog, pointer, listOfNotNull(saved)).staleReason != null) {
            progressRepository.markStale(pointer)
        }
    }
}
