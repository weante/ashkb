package com.ashkb.app.ui.emergency

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
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
import com.ashkb.app.data.repo.ReportRepository
import com.ashkb.app.ui.report.ReportPdfWriter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class EmergencyViewModel(
    private val app: Context,
    private val repo: HealthRepository,
    private val reports: ReportRepository,
) : ViewModel() {

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

    /** M7 紧急卡打印版 PDF（供急救人员参考，白底打印友好）。 */
    fun exportCardPdf(onReady: (Intent) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val card = reports.emergencyCard()
                val pdf = ReportPdfWriter.writeEmergencyCard(app, card)
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", pdf)
                onReady(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_SUBJECT, File(pdf.name).nameWithoutExtension)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                )
            } catch (e: Exception) {
                onError("紧急卡 PDF 生成失败：${e.message}")
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                EmergencyViewModel(app.applicationContext, app.healthRepository, app.reportRepository)
            }
        }
    }
}
