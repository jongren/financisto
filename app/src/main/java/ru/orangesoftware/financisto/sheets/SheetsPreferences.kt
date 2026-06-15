package ru.orangesoftware.financisto.sheets

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper for Google Sheets sync settings.
 */
object SheetsPreferences {

    private const val PREFS_NAME = "google_sheets_sync"
    private const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    private const val KEY_ACCOUNT_EMAIL = "account_email"
    private const val KEY_SYNC_PAYEE_IDS = "sync_payee_ids"
    private const val KEY_MONTHLY_BUDGET = "monthly_budget"
    private const val KEY_AUTO_SYNC_MODE = "auto_sync_mode"
    private const val KEY_SYNC_YEAR = "sync_year"

    // Auto sync modes
    const val SYNC_MODE_DISABLED = 0
    const val SYNC_MODE_DAILY = 1
    const val SYNC_MODE_WEEKLY = 2

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getMonthlyBudget(context: Context): String =
        prefs(context).getString(KEY_MONTHLY_BUDGET, "") ?: ""

    fun saveMonthlyBudget(context: Context, budget: String) {
        prefs(context).edit().putString(KEY_MONTHLY_BUDGET, budget).apply()
    }

    fun getSpreadsheetId(context: Context): String? =
        prefs(context).getString(KEY_SPREADSHEET_ID, null)

    fun saveSpreadsheetId(context: Context, id: String) {
        prefs(context).edit().putString(KEY_SPREADSHEET_ID, id).apply()
    }

    fun getAccountEmail(context: Context): String? =
        prefs(context).getString(KEY_ACCOUNT_EMAIL, null)

    fun saveAccountEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_ACCOUNT_EMAIL, email).apply()
    }

    fun clearAccount(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCOUNT_EMAIL)
            .remove(KEY_SPREADSHEET_ID)
            .apply()
    }

    /** Returns the set of payee IDs that should be synced. */
    fun getSyncPayeeIds(context: Context): Set<Long> {
        val raw = prefs(context).getString(KEY_SYNC_PAYEE_IDS, "") ?: ""
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun saveSyncPayeeIds(context: Context, ids: Set<Long>) {
        prefs(context).edit()
            .putString(KEY_SYNC_PAYEE_IDS, ids.joinToString(","))
            .apply()
    }

    fun getAutoSyncMode(context: Context): Int =
        prefs(context).getInt(KEY_AUTO_SYNC_MODE, SYNC_MODE_DISABLED)

    fun saveAutoSyncMode(context: Context, mode: Int) {
        prefs(context).edit().putInt(KEY_AUTO_SYNC_MODE, mode).apply()
    }

    fun getSyncYear(context: Context): Int =
        prefs(context).getInt(KEY_SYNC_YEAR, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))

    fun saveSyncYear(context: Context, year: Int) {
        prefs(context).edit().putInt(KEY_SYNC_YEAR, year).apply()
    }

    fun isLinked(context: Context): Boolean = getAccountEmail(context) != null
}
