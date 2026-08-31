package mia.chinese

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mia.chinese.data.CatalogRepository
import mia.chinese.data.CatalogValidator
import mia.chinese.data.LastResumePointerEntity
import mia.chinese.data.ProgressRepository
import mia.chinese.data.VideoProgressEntity
import mia.chinese.model.Catalog

sealed interface CatalogLoadState {
    data object Loading : CatalogLoadState
    data class Ready(val catalog: Catalog) : CatalogLoadState
    data class Error(val message: String) : CatalogLoadState
}

class MainViewModel(
    private val catalogRepository: CatalogRepository,
    progressRepository: ProgressRepository
) : ViewModel() {
    private val _catalogState = MutableStateFlow<CatalogLoadState>(CatalogLoadState.Loading)
    val catalogState: StateFlow<CatalogLoadState> = _catalogState.asStateFlow()

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

    init {
        loadCatalog()
    }

    fun retry() {
        loadCatalog()
    }

    private fun loadCatalog() {
        _catalogState.value = CatalogLoadState.Loading
        viewModelScope.launch {
            runCatching { catalogRepository.loadBundledCatalog() }
                .mapCatching { CatalogValidator.requireValid(it) }
                .onSuccess { _catalogState.value = CatalogLoadState.Ready(it) }
                .onFailure {
                    _catalogState.value = CatalogLoadState.Error(
                        it.message ?: "無法載入課程資料"
                    )
                }
        }
    }
}
