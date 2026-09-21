package com.ashkb.app.ui.checkup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import com.ashkb.app.AshkbApplication
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.CheckupType
import com.ashkb.app.data.entity.ImagingRecord
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.repo.HealthRepository
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.LabImport
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CheckupViewModel(
    private val repo: HealthRepository,
    private val attachmentRepo: com.ashkb.app.data.repo.AttachmentRepository,
) : ViewModel() {
    private val _date = MutableStateFlow(LocalDate.now())
    val date: LocalDate get() = _date.value

    init {
        viewModelScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                delay(Duration.between(now, nextMidnight).toMillis() + 1_000L)
                _date.value = LocalDate.now()
            }
        }
    }

    val checkupItems: StateFlow<List<CheckupItem>> = repo.observeCheckupItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val checkupRecords: StateFlow<List<CheckupRecord>> = repo.observeCheckupRecent(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vaccineRecords: StateFlow<List<VaccineRecord>> = repo.observeVaccinesAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _labLimit = MutableStateFlow(100)
    val labLimit: StateFlow<Int> = _labLimit.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val labRecent: StateFlow<List<LabResult>> = _labLimit
        .flatMapLatest { repo.observeLabRecent(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val imagingRecords: StateFlow<List<ImagingRecord>> = repo.observeImagingRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- 复诊项目 ----
    fun saveCheckupItem(item: CheckupItem) {
        viewModelScope.launch { repo.saveCheckupItem(item) }
    }

    fun deactivateCheckupItem(id: String) {
        viewModelScope.launch { repo.deactivateCheckupItem(id) }
    }

    // ---- 复诊记录 ----
    fun saveCheckupRecord(record: CheckupRecord) {
        viewModelScope.launch { repo.saveCheckupRecord(record) }
    }

    // ---- 化验结果 ----
    // v1.0.28：不再在 VM 内缓存 StateFlow（原实现无界增长 + 非线程安全 + onCleared 不清理）。
    // 改为返回冷 Flow，由调用点 remember(record.id) 记住订阅——符合「状态在 UI 层记住」的 Compose 哲学，
    // 也保证同一条记录 Flow identity 稳定、切换记录时自动重订阅。
    fun labResultsFor(checkupId: String): Flow<List<LabResult>> =
        repo.observeLabByCheckup(checkupId)

    fun saveLabResult(result: LabResult) {
        viewModelScope.launch { repo.saveLabResult(result) }
    }

    fun loadMoreLabs() {
        _labLimit.value += 100
    }

    fun importLabReport(import: LabImport) {
        viewModelScope.launch { repo.importLabReport(import) }
    }

    // ---- 影像记录（v1.0.4 AI 导入） ----
    fun saveImagingRecord(record: ImagingRecord) {
        viewModelScope.launch { repo.saveImagingRecord(record) }
    }

    fun importImagingReport(import: ImagingImport) {
        viewModelScope.launch { repo.importImagingReport(import) }
    }

    // ---- 疫苗记录 ----
    fun saveVaccineRecord(record: VaccineRecord) {
        viewModelScope.launch { repo.saveVaccineRecord(record) }
    }

    // ---- v10（B10）复诊附件归档：拍照 / 相册 / PDF ----
    val attachments: StateFlow<List<com.ashkb.app.data.entity.CheckupAttachment>> =
        attachmentRepo.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 某条复诊记录下的附件（冷 Flow，调用点 remember(id) 记住） */
    fun attachmentsFor(checkupId: String): Flow<List<com.ashkb.app.data.entity.CheckupAttachment>> =
        attachmentRepo.observeByCheckup(checkupId)

    /** 相机目标文件 + 其 content URI（交给 TakePicture 契约写入） */
    fun newCameraTarget(): Pair<java.io.File, android.net.Uri> = attachmentRepo.newCameraTarget()

    fun discardCameraFile(file: java.io.File) = attachmentRepo.discardCameraFile(file)

    /** 拍照成功后入库；返回 null = 失败（空文件 / 超限） */
    suspend fun registerCameraPhoto(
        file: java.io.File,
        checkupId: String?,
    ): com.ashkb.app.data.entity.CheckupAttachment? =
        attachmentRepo.registerCameraFile(file, checkupId)

    /** 相册图片 / PDF 导入；返回 null = 失败 */
    suspend fun importAttachment(
        uri: android.net.Uri,
        kind: String,
        checkupId: String?,
        displayName: String?,
    ): com.ashkb.app.data.entity.CheckupAttachment? =
        attachmentRepo.importFromUri(uri, kind, checkupId, displayName)

    fun deleteAttachment(attachment: com.ashkb.app.data.entity.CheckupAttachment) {
        viewModelScope.launch { attachmentRepo.delete(attachment) }
    }

    /** 供分享 / 外部查看的 content URI */
    fun attachmentUri(attachment: com.ashkb.app.data.entity.CheckupAttachment): android.net.Uri =
        attachmentRepo.uriOf(attachment)

    // ---- v11：化验 / 影像归属复诊记录（手动选择，不按日期自动猜） ----

    /** 把某一天的全部化验归属到复诊记录（null = 解除归属） */
    fun linkLabsByDate(date: String, checkupId: String?) {
        viewModelScope.launch { repo.linkLabsByDate(date, checkupId) }
    }

    /** 把某条影像归属到复诊记录（null = 解除归属） */
    fun linkImaging(imagingId: String, checkupId: String?) {
        viewModelScope.launch { repo.linkImagingToCheckup(imagingId, checkupId) }
    }

    /** 改附件归属（null = 解除归属） */
    fun linkAttachment(attachmentId: String, checkupId: String?) {
        viewModelScope.launch { attachmentRepo.linkToCheckup(attachmentId, checkupId) }
    }

    /** 某条复诊记录下的影像（冷 Flow，调用点 remember(id) 记住） */
    fun imagingFor(checkupId: String): Flow<List<ImagingRecord>> = repo.observeImagingByCheckup(checkupId)

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                CheckupViewModel(app.healthRepository, app.attachmentRepository)
            }
        }
    }
}
