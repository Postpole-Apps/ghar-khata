package com.postpole.gharkhata.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.postpole.gharkhata.data.ProviderEntity
import com.postpole.gharkhata.data.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Placeholder Home: lists active providers. The real Today screen arrives in G5. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: ProviderRepository,
) : ViewModel() {

    val providers: StateFlow<List<ProviderEntity>> = repository.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
