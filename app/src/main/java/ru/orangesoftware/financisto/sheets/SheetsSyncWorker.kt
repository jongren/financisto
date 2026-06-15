package ru.orangesoftware.financisto.sheets

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import ru.orangesoftware.financisto.db.DatabaseAdapter
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * WorkManager Worker that runs the Google Sheets sync in the background.
 * Handles both manual (one-shot) and scheduled (periodic) execution.
 */
class SheetsSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val WORK_NAME_PERIODIC = "sheets_sync_periodic"

        /** Schedule or cancel periodic sync based on saved preferences. */
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            when (SheetsPreferences.getAutoSyncMode(context)) {
                SheetsPreferences.SYNC_MODE_DAILY -> {
                    val request = PeriodicWorkRequestBuilder<SheetsSyncWorker>(
                        1, TimeUnit.DAYS
                    ).setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    ).build()
                    wm.enqueueUniquePeriodicWork(
                        WORK_NAME_PERIODIC,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
                }
                SheetsPreferences.SYNC_MODE_WEEKLY -> {
                    val request = PeriodicWorkRequestBuilder<SheetsSyncWorker>(
                        7, TimeUnit.DAYS
                    ).setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    ).build()
                    wm.enqueueUniquePeriodicWork(
                        WORK_NAME_PERIODIC,
                        ExistingPeriodicWorkPolicy.REPLACE,
                        request
                    )
                }
                else -> {
                    wm.cancelUniqueWork(WORK_NAME_PERIODIC)
                }
            }
        }

        /** Trigger an immediate one-shot sync. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SheetsSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                ).build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext

        val accountName = SheetsPreferences.getAccountEmail(ctx) ?: return Result.failure()
        val payeeIds = SheetsPreferences.getSyncPayeeIds(ctx)
        if (payeeIds.isEmpty()) return Result.failure()

        return try {
            val credential = SheetsAuthHelper.buildCredential(ctx, accountName)

            val db = DatabaseAdapter(ctx)
            val exporter = TransactionExporter(ctx)
            val sync = GoogleSheetsSync(credential)

            val budgetStr = SheetsPreferences.getMonthlyBudget(ctx)
            val budget = budgetStr.toDoubleOrNull() ?: 0.0

            val year = SheetsPreferences.getSyncYear(ctx)
            val existingId = SheetsPreferences.getSpreadsheetId(ctx)

            val dataByMonth = exporter.exportByMonth(db, year, payeeIds)
            if (dataByMonth.isEmpty()) return Result.success()

            val spreadsheetId = sync.getOrCreateSpreadsheet(year, existingId)
            SheetsPreferences.saveSpreadsheetId(ctx, spreadsheetId)
            sync.syncYear(spreadsheetId, dataByMonth, budget)

            Result.success()
        } catch (e: UserRecoverableAuthIOException) {
            // Sheets scope not consented yet — requires user interaction, skip retry
            Result.failure()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
