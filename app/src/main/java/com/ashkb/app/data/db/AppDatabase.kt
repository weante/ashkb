package com.ashkb.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ashkb.app.data.entity.Alert
import com.ashkb.app.data.entity.BackupLedger
import com.ashkb.app.data.entity.BasdaiRecord
import com.ashkb.app.data.entity.BodyMeasure
import com.ashkb.app.data.entity.CheckupItem
import com.ashkb.app.data.entity.CheckupRecord
import com.ashkb.app.data.entity.DietProfile
import com.ashkb.app.data.entity.EmergencyContact
import com.ashkb.app.data.entity.EmergencyEvent
import com.ashkb.app.data.entity.ExerciseLog
import com.ashkb.app.data.entity.FlareEvent
import com.ashkb.app.data.entity.FoodAvoidItem
import com.ashkb.app.data.entity.KbEntry
import com.ashkb.app.data.entity.LabResult
import com.ashkb.app.data.entity.Medication
import com.ashkb.app.data.entity.MedicationChange
import com.ashkb.app.data.entity.MedicationLog
import com.ashkb.app.data.entity.Profile
import com.ashkb.app.data.entity.Supplement
import com.ashkb.app.data.entity.SupplementLog
import com.ashkb.app.data.entity.SymptomDaily
import com.ashkb.app.data.entity.VaccineRecord
import com.ashkb.app.data.entity.Vitals
import com.ashkb.app.data.entity.WeightLog

@Database(
    entities = [
        Profile::class, Medication::class, MedicationLog::class, KbEntry::class, MedicationChange::class,
        SymptomDaily::class, BasdaiRecord::class, FlareEvent::class, ExerciseLog::class, Alert::class,
        Supplement::class, SupplementLog::class, Vitals::class, WeightLog::class, BodyMeasure::class,
        DietProfile::class, FoodAvoidItem::class, CheckupItem::class, CheckupRecord::class, LabResult::class,
        VaccineRecord::class, EmergencyEvent::class, EmergencyContact::class, BackupLedger::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun medicationDao(): MedicationDao
    abstract fun medicationLogDao(): MedicationLogDao
    abstract fun kbEntryDao(): KbEntryDao
    abstract fun medicationChangeDao(): MedicationChangeDao
    abstract fun symptomDailyDao(): SymptomDailyDao
    abstract fun basdaiDao(): BasdaiDao
    abstract fun flareDao(): FlareDao
    abstract fun exerciseLogDao(): ExerciseLogDao
    abstract fun alertDao(): AlertDao
    abstract fun supplementDao(): SupplementDao
    abstract fun supplementLogDao(): SupplementLogDao
    abstract fun vitalsDao(): VitalsDao
    abstract fun weightLogDao(): WeightLogDao
    abstract fun bodyMeasureDao(): BodyMeasureDao
    abstract fun dietProfileDao(): DietProfileDao
    abstract fun foodAvoidItemDao(): FoodAvoidItemDao
    abstract fun checkupItemDao(): CheckupItemDao
    abstract fun checkupRecordDao(): CheckupRecordDao
    abstract fun labResultDao(): LabResultDao
    abstract fun vaccineRecordDao(): VaccineRecordDao
    abstract fun emergencyEventDao(): EmergencyEventDao
    abstract fun contactDao(): ContactDao
    abstract fun backupLedgerDao(): BackupLedgerDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v2：新增 medication_changes（R17 停药原因分类） */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medication_changes` (" +
                        "`id` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, `backfill` INTEGER NOT NULL, " +
                        "`med_id` TEXT, `med_key` TEXT NOT NULL, `change_type` TEXT NOT NULL, " +
                        "`old_snapshot` TEXT NOT NULL, `new_snapshot` TEXT NOT NULL, " +
                        "`effective_date` TEXT NOT NULL, `reason` TEXT NOT NULL, `reason_note` TEXT, " +
                        "`source` TEXT NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_changes_med_id` ON `medication_changes` (`med_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_medication_changes_effective_date` ON `medication_changes` (`effective_date`)")
            }
        }

        /** v3：P2 五表——symptom_daily / basdai_records / flare_events / exercise_logs / alerts */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `symptom_daily` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `morning_stiffness_min` INTEGER, `night_pain` INTEGER, " +
                        "`pain_score` INTEGER, `feverish` INTEGER NOT NULL, `fever_temp` REAL, " +
                        "`eye_symptom` INTEGER NOT NULL, `neuro_red_flag` INTEGER NOT NULL, " +
                        "`mood` INTEGER, `sleep` INTEGER, `fatigue` INTEGER, `notes` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_symptom_daily_date` ON `symptom_daily` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `basdai_records` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `q1_fatigue` INTEGER NOT NULL, `q2_spine_pain` INTEGER NOT NULL, " +
                        "`q3_peripheral_pain` INTEGER NOT NULL, `q4_tender_points` INTEGER NOT NULL, " +
                        "`q5_stiffness_degree` INTEGER NOT NULL, `q6_stiffness_duration` INTEGER NOT NULL, " +
                        "`total` REAL NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_basdai_records_date` ON `basdai_records` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `flare_events` (" +
                        "`id` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, `start_date` TEXT NOT NULL, " +
                        "`end_date` TEXT, `status` TEXT NOT NULL, `trigger` TEXT NOT NULL, " +
                        "`actions_taken` TEXT, `severity_peak` INTEGER, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flare_events_start_date` ON `flare_events` (`start_date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_flare_events_status` ON `flare_events` (`status`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exercise_logs` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `exc_id` TEXT, `exc_key` TEXT NOT NULL, " +
                        "`exc_name` TEXT NOT NULL, `grade` TEXT, `duration_min` INTEGER, `intensity` TEXT, " +
                        "`status` TEXT NOT NULL, `reason` TEXT, `fb_pain_change` TEXT, `fb_stiffness_change` TEXT, " +
                        "`fb_is_muscle_soreness` INTEGER, `fb_note` TEXT, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_logs_date` ON `exercise_logs` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exercise_logs_exc_id` ON `exercise_logs` (`exc_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alerts` (" +
                        "`id` TEXT NOT NULL, `alert_type` TEXT NOT NULL, `severity` TEXT NOT NULL, " +
                        "`message` TEXT NOT NULL, `kb_ref` TEXT, `ref_date` TEXT, `created_at` TEXT NOT NULL, " +
                        "`ack_at` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_alert_type` ON `alerts` (`alert_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_alerts_created_at` ON `alerts` (`created_at`)")
            }
        }

        /** v4：P3 十三表——M2/M3 营养体征 + M6 复诊 + M7 紧急卡 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---- M2 补剂 ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `supplements` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `brand` TEXT, `category` TEXT NOT NULL, " +
                        "`dose` TEXT NOT NULL, `frequency` TEXT NOT NULL, `times` TEXT, `take_with_food` TEXT, " +
                        "`prescribed` INTEGER NOT NULL, `is_archived` INTEGER NOT NULL, `notes` TEXT, " +
                        "`created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplements_is_archived` ON `supplements` (`is_archived`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `supplement_logs` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `sup_id` TEXT, `sup_key` TEXT NOT NULL, " +
                        "`sup_name` TEXT NOT NULL, `dose_snapshot` TEXT NOT NULL, `scheduled_time` TEXT, " +
                        "`slot_key` TEXT, `status` TEXT NOT NULL, `reason` TEXT, `taken_at` TEXT, " +
                        "`notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplement_logs_date` ON `supplement_logs` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_supplement_logs_sup_id` ON `supplement_logs` (`sup_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_supplement_logs_date_sup_id_slot_key` ON `supplement_logs` (`date`, `sup_id`, `slot_key`)")
                // ---- M3 体征 / 体重 / 身体指标 ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vitals` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `temperature` REAL, `bp_sys` INTEGER, `bp_dia` INTEGER, " +
                        "`heart_rate` INTEGER, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vitals_date` ON `vitals` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vitals_recorded_at` ON `vitals` (`recorded_at`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_logs` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `weight_kg` REAL NOT NULL, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_weight_logs_date` ON `weight_logs` (`date`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_measures` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `height_cm` REAL, `waist_cm` REAL, `hip_cm` REAL, " +
                        "`bmi` REAL, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_body_measures_date` ON `body_measures` (`date`)")
                // ---- M3 饮食画像 + 忌口 ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `diet_profile` (" +
                        "`id` INTEGER NOT NULL, `diet_pattern` TEXT NOT NULL, `seafood_freq` TEXT, " +
                        "`dairy_tolerant` TEXT, `alcohol_freq` TEXT, `caffeine_freq` TEXT, `notes` TEXT, " +
                        "`updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `food_avoid_items` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                        "`severity` TEXT NOT NULL, `kb_ref` TEXT, `symptoms` TEXT, `notes` TEXT, " +
                        "`created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_food_avoid_items_category` ON `food_avoid_items` (`category`)")
                // ---- M6 复诊 ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checkup_items` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `check_type` TEXT NOT NULL, " +
                        "`cycle_days` INTEGER, `linked_med_id` TEXT, `kb_ref` TEXT, " +
                        "`is_active` INTEGER NOT NULL, `notes` TEXT, " +
                        "`created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkup_items_check_type` ON `checkup_items` (`check_type`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkup_items_is_active` ON `checkup_items` (`is_active`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `checkup_records` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `item_id` TEXT, `item_name` TEXT NOT NULL, " +
                        "`check_type` TEXT NOT NULL, `status` TEXT NOT NULL, `hospital` TEXT, " +
                        "`doctor` TEXT, `next_date` TEXT, `conclusion` TEXT, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkup_records_date` ON `checkup_records` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_checkup_records_item_id` ON `checkup_records` (`item_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lab_results` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `checkup_id` TEXT, `test_name` TEXT NOT NULL, " +
                        "`value` REAL, `value_text` TEXT, `unit` TEXT, `ref_low` REAL, `ref_high` REAL, " +
                        "`abnormal` TEXT, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_results_date` ON `lab_results` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_results_test_name` ON `lab_results` (`test_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_lab_results_checkup_id` ON `lab_results` (`checkup_id`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vaccine_records` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`backfill` INTEGER NOT NULL, `vaccine_name` TEXT NOT NULL, `vaccine_type` TEXT NOT NULL, " +
                        "`dose` TEXT, `hospital` TEXT, `doctor_confirm` TEXT NOT NULL, `reaction` TEXT, " +
                        "`next_due_date` TEXT, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vaccine_records_date` ON `vaccine_records` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vaccine_records_vaccine_type` ON `vaccine_records` (`vaccine_type`)")
                // ---- M7 紧急卡 ----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `emergency_events` (" +
                        "`id` TEXT NOT NULL, `date` TEXT NOT NULL, `recorded_at` TEXT NOT NULL, " +
                        "`scene` TEXT NOT NULL, `severity` TEXT NOT NULL, `onset_time` TEXT, " +
                        "`symptoms` TEXT, `actions_taken` TEXT, `hospital_visit` INTEGER NOT NULL, " +
                        "`hospital_name` TEXT, `outcome` TEXT, `resolved_date` TEXT, `notes` TEXT, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_emergency_events_date` ON `emergency_events` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_emergency_events_scene` ON `emergency_events` (`scene`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `contacts` (" +
                        "`id` TEXT NOT NULL, `name` TEXT NOT NULL, `relation` TEXT, `phone` TEXT NOT NULL, " +
                        "`is_emergency` INTEGER NOT NULL, `is_doctor` INTEGER NOT NULL, `hospital` TEXT, " +
                        "`notes` TEXT, `created_at` TEXT NOT NULL, `updated_at` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_contacts_is_emergency` ON `contacts` (`is_emergency`)")
            }
        }

        /** v5：P4 备份台账（R20 台账登记语义） */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `backup_ledger` (" +
                        "`id` TEXT NOT NULL, `ledger_type` TEXT NOT NULL, `status` TEXT NOT NULL, " +
                        "`target` TEXT NOT NULL, `file_name` TEXT, `row_total` INTEGER, " +
                        "`verify_ok` INTEGER, `detail` TEXT, `created_at` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_backup_ledger_created_at` ON `backup_ledger` (`created_at`)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, AppDatabase::class.java, "ashkb.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}

object Ids {
    /** 语义化主键：前缀 + 时间基 36 进制 + 随机尾，本地唯一即可 */
    fun new(prefix: String): String {
        val t = System.currentTimeMillis().toString(36)
        val r = (0..2).map { "0123456789abcdefghijklmnopqrstuvwxyz"[(0..35).random()] }.joinToString("")
        return "$prefix-$t$r"
    }
}
