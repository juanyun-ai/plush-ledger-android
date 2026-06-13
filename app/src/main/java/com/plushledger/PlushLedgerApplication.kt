package com.plushledger

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.plushledger.auth.AuthRepository
import com.plushledger.auth.SessionStore
import com.plushledger.data.AppDatabase
import com.plushledger.data.LedgerRepository
import com.plushledger.security.SecureStore
import com.plushledger.sync.SyncWorker
import com.plushledger.sync.SupabaseClient
import java.util.concurrent.TimeUnit

class PlushLedgerApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleSync()
    }

    private fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ledger-cloud-sync",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun enqueueImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "ledger-cloud-sync-now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}

class AppContainer(application: Application) {
    val database: AppDatabase = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "plush-ledger.db"
    ).addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3).build()

    val secureStore = SecureStore(application)
    val sessionStore = SessionStore(secureStore)
    val supabaseClient = SupabaseClient()
    val authRepository = AuthRepository(sessionStore, supabaseClient)
    val ledgerRepository = LedgerRepository(application, database.ledgerDao(), sessionStore, supabaseClient)
}
