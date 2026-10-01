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
import com.ashkb.app.data.repo.ReminderConfigRepository
import com.ashkb.app.domain.CheckupDeletion
import com.ashkb.app.domain.ImagingImport
import com.ashkb.app.domain.LabImport
import com.ashkb.app.reminder.CheckupReminderScheduler
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
    private val backupRepo: com.ashkb.app.data.repo.BackupRepository,
    private val app: AshkbApplication,
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

    // ---- C10 生物制剂筛查 / 续方节点一键种入 ----
    // 结果暴露 Int? 而非文案：本 VM 不持有 Context，文案由 UI 侧 stringResource 组装，
    // 语言区域切换时无需重建 VM 也能正确取词。
    private val _seedResult = MutableStateFlow<Int?>(null)
    val seedResult: StateFlow<Int?> = _seedResult.asStateFlow()

    /** 幂等种入结核 / 乙肝 / 丙肝筛查 + 生物制剂续方随访节点，结果为本次新增条数。 */
    fun seedBiologicScreening() {
        // 先清旧结果：连点按钮时不会残留上一次的"已添加 N 个节点"
        _seedResult.value = null
        viewModelScope.launch { _seedResult.value = repo.seedBiologicScreeningItems() }
    }

    /** UI 展示过结果后清空（本页退出时调用），避免下次进入看到过期文案。 */
    fun consumeSeedResult() {
        _seedResult.value = null
    }

    // ---- 复诊记录 ----
    fun saveCheckupRecord(record: CheckupRecord) {
        viewModelScope.launch {
            repo.saveCheckupRecord(record)
            rescheduleCheckupReminders()
        }
    }

    /**
     * v1.0.80（批次 6）：删除复诊记录（**级联**删掉名下化验 / 影像 / 附件）。
     *
     * 删完必须重排提醒：复诊提醒是由记录的 `nextDate` 推出来的（见 [CheckupReminderScheduler]），
     * 记录没了而闹钟还在，用户会在「下次复诊日」收到一条指向已删记录的提醒——典型的派生数据没跟着重算。
     */
    fun deleteCheckupRecord(id: String) {
        viewModelScope.launch {
            repo.deleteCheckupRecord(id)
            rescheduleCheckupReminders()
        }
    }

    /** 删除确认框要报出的级联条数（打开确认框时查一次；失败返回 null，UI 保持确认按钮禁用）。 */
    suspend fun checkupDeletionCounts(id: String): CheckupDeletion.Counts? =
        runCatching { repo.checkupDeletionCounts(id) }.getOrNull()

    /**
     * 复诊提醒重排：增 / 改 / 删复诊记录后都要走这里。
     * 三条路径各写一遍必然漏改一处，而漏改的症状是「提醒与记录不一致」——从界面上看不出来。
     */
    private suspend fun rescheduleCheckupReminders() {
        val cfg = ReminderConfigRepository(app)
        val all = com.ashkb.app.data.db.AppDatabase.get(app).checkupRecordDao().listAll()
        if (cfg.checkupEnabled()) {
            runCatching {
                CheckupReminderScheduler.rescheduleAll(
                    app, all, LocalDate.now(), LocalDateTime.now(),
                )
            }
        } else {
            CheckupReminderScheduler.cancelAllFuture(app, all)
        }
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

    /**
     * v1.0.80（批次 6）：删除单条化验结果。
     * 无派生数据（`abnormal` 是这一行自己的字段、化验不产生警报），故不需要任何重算。
     */
    fun deleteLabResult(id: String) {
        viewModelScope.launch { repo.deleteLabResult(id) }
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

    /** v1.0.80（批次 6）：删除单条影像记录（附件挂复诊记录，不挂影像，故无级联）。 */
    fun deleteImagingRecord(id: String) {
        viewModelScope.launch { repo.deleteImagingRecord(id) }
    }

    fun importImagingReport(import: ImagingImport) {
        viewModelScope.launch { repo.importImagingReport(import) }
    }

    // ---- 疫苗记录 ----
    fun saveVaccineRecord(record: VaccineRecord) {
        viewModelScope.launch { repo.saveVaccineRecord(record) }
    }

    /**
     * v1.0.80（批次 6）：删除疫苗记录。
     * 仓库层会**重算**该接种日的活疫苗安全警报（当天还有别的活疫苗待确认时保留，见 repo 注释），
     * 故这里不需要额外处理。
     */
    fun deleteVaccineRecord(id: String) {
        viewModelScope.launch { repo.deleteVaccineRecord(id) }
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

    // ---- v12（v1.0.35）附件同步：本地缺失时按需从云端取回 ----

    /** 本地是否已有该附件文件（否则「查看」需先懒下载）。 */
    fun attachmentHasLocal(a: com.ashkb.app.data.entity.CheckupAttachment): Boolean =
        attachmentRepo.hasLocalFile(a)

    /** 附件同步开关（只读，用于列表状态标记）。 */
    fun attachmentSyncOn(): Boolean = backupRepo.attachmentSyncEnabled()

    /**
     * 懒下载：本地文件缺失且已有远端副本时按需取回并解密。
     * @return true = 本地已可用（原本就在 / 刚取回）；false = 云端没有或解密失败
     */
    suspend fun ensureAttachmentLocal(a: com.ashkb.app.data.entity.CheckupAttachment): Boolean =
        backupRepo.downloadAttachmentIfMissing(a)

    companion object {
        val Factory: ViewModelProvider.Factory = androidx.lifecycle.viewmodel.viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as AshkbApplication
                CheckupViewModel(app.healthRepository, app.attachmentRepository, app.backupRepository, app)
            }
        }
    }
}
