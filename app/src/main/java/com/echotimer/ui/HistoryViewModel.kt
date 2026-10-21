package com.echotimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.echotimer.EchoTimerApplication
import com.echotimer.data.HistoryRepository
import com.echotimer.data.TimingSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** 历史记录页的编排层。 */
class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {

    val sessions: StateFlow<List<TimingSession>> = repository.sessions

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun delete(sessionId: Long) {
        viewModelScope.launch { repository.delete(sessionId) }
    }

    fun rename(sessionId: Long, note: String) {
        viewModelScope.launch { repository.rename(sessionId, note) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as EchoTimerApplication
                HistoryViewModel(app.container.historyRepository)
            }
        }
    }
}
