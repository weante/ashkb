package com.ashkb.app.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TEMP (clone-only) — proof that the backfilled historical schema files (4.json … 16.json)
 * are actually sufficient to drive the real upgrade path through MigrationTestHelper.
 *
 * For each historical start version it:
 *   1. builds an old database from `schemas/…/<N>.json`  (createDatabase)
 *   2. runs the real migration chain N → ASHKB_DB_VERSION (runMigrationsAndValidate)
 *      — Room additionally validates the resulting structure against 19.json
 *
 * Not part of the main repo: this file exists only in the scratch clone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class MigrationPathProofTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private fun upgradeFrom(startVersion: Int) {
        val name = "backfill-proof-$startVersion.db"
        helper.createDatabase(name, startVersion).close()
        helper.runMigrationsAndValidate(
            name,
            ASHKB_DB_VERSION,
            true,
            *AppDatabase.ALL_MIGRATIONS,
        ).close()
    }

    @Test fun `upgrade from v4`() = upgradeFrom(4)
    @Test fun `upgrade from v5`() = upgradeFrom(5)
    @Test fun `upgrade from v6`() = upgradeFrom(6)
    @Test fun `upgrade from v7`() = upgradeFrom(7)
    @Test fun `upgrade from v8`() = upgradeFrom(8)
    @Test fun `upgrade from v9`() = upgradeFrom(9)
    @Test fun `upgrade from v10`() = upgradeFrom(10)
    @Test fun `upgrade from v11`() = upgradeFrom(11)
    @Test fun `upgrade from v12`() = upgradeFrom(12)
    @Test fun `upgrade from v13`() = upgradeFrom(13)
    @Test fun `upgrade from v14`() = upgradeFrom(14)
    @Test fun `upgrade from v15`() = upgradeFrom(15)
    @Test fun `upgrade from v16`() = upgradeFrom(16)
}
