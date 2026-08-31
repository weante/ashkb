package com.ashkb.app.ui.emergency

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.EmergencyScene
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.repo.HealthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EmergencyViewModel(private val repo: HealthRepository) : ViewModel() {

    val contacts: StateFlow<List<EmergencyContact>> = repo.observeEmergencyContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val events: StateFlow<List<EmergencyEvent>> = repo.observeEmergencyRecent(20)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val profile: StateFlow<com.ashkb.app.data.entity.Profile?> = repo.observeProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun saveContact(contact: EmergencyContact) {
        viewModelScope.launch { repo.saveContact(contact) }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { repo.deleteContact(id) }
    }

    fun saveEmergencyEvent(event: EmergencyEvent) {
        viewModelScope.launch { repo.saveEmergencyEvent(event) }
    }

    suspend fun emergencyCards(): List<KbEntry> = repo.kbByCategory("emergency")

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                EmergencyViewModel(app.healthRepository)
            }
        }
    }
}
