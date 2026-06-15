package ru.orangesoftware.financisto.activity

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.model.Payee
import ru.orangesoftware.financisto.sheets.GoogleSheetsSync
import ru.orangesoftware.financisto.sheets.SheetsAuthHelper
import ru.orangesoftware.financisto.sheets.SheetsPreferences
import ru.orangesoftware.financisto.sheets.SheetsSyncWorker
import ru.orangesoftware.financisto.sheets.TransactionExporter
import java.util.Calendar

/**
 * Settings screen for Google Sheets sync.
 *
 * Account selection uses Android's built-in AccountPicker —
 * the user just picks one of the Google accounts already on the device.
 * No separate Google Sign-In or web OAuth flow required.
 */
class GoogleSheetsSyncActivity : AppCompatActivity() {

    companion object {
        private const val RC_AUTHORIZATION = 9003  // Sheets consent dialog
    }

    private lateinit var tvAccountStatus: TextView
    private lateinit var btnPickAccount: Button
    private lateinit var btnClearAccount: Button
    private lateinit var llPayeeContainer: LinearLayout
    private lateinit var spinnerFrequency: Spinner
    private lateinit var spinnerYear: Spinner
    private lateinit var btnSaveSettings: Button
    private lateinit var btnSyncNow: Button
    private lateinit var etMonthlyBudget: EditText

    private val payeeCheckBoxes = mutableListOf<Pair<CheckBox, Long>>()
    private var yearsList = listOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google_sheets_sync)
        supportActionBar?.apply {
            title = getString(R.string.google_sheets_sync)
            setDisplayHomeAsUpEnabled(true)
        }

        tvAccountStatus = findViewById(R.id.tv_account_status)
        btnPickAccount = findViewById(R.id.btn_link_account)
        btnClearAccount = findViewById(R.id.btn_unlink_account)
        llPayeeContainer = findViewById(R.id.ll_payee_container)
        spinnerFrequency = findViewById(R.id.spinner_sync_frequency)
        spinnerYear = findViewById(R.id.spinner_sync_year)
        btnSaveSettings = findViewById(R.id.btn_save_settings)
        btnSyncNow = findViewById(R.id.btn_sync_now)
        etMonthlyBudget = findViewById(R.id.et_monthly_budget)
        etMonthlyBudget.setText(SheetsPreferences.getMonthlyBudget(this))

        setupFrequencySpinner()
        setupYearSpinner()
        loadPayees()
        updateAccountUI()

        btnPickAccount.setOnClickListener {
            val accounts = SheetsAuthHelper.getAvailableAccounts(this)
            when {
                // If only one account, select it directly without picker
                accounts.size == 1 -> {
                    SheetsPreferences.saveAccountEmail(this, accounts[0])
                    updateAccountUI()
                    Toast.makeText(this, getString(R.string.sheets_account_linked), Toast.LENGTH_SHORT).show()
                }
                else -> startActivityForResult(
                    SheetsAuthHelper.getAccountPickerIntent(),
                    SheetsAuthHelper.RC_ACCOUNT_PICKER
                )
            }
        }

        btnClearAccount.setOnClickListener {
            SheetsPreferences.clearAccount(this)
            SheetsSyncWorker.reschedule(this)
            updateAccountUI()
            Toast.makeText(this, R.string.sheets_account_unlinked, Toast.LENGTH_SHORT).show()
        }

        btnSaveSettings.setOnClickListener { saveSettings() }
        btnSyncNow.setOnClickListener { performSync() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            SheetsAuthHelper.RC_ACCOUNT_PICKER -> {
                if (resultCode == RESULT_OK) {
                    val email = SheetsAuthHelper.handleAccountPickerResult(data)
                    if (email != null) {
                        SheetsPreferences.saveAccountEmail(this, email)
                        updateAccountUI()
                        Toast.makeText(this, getString(R.string.sheets_account_linked), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            RC_AUTHORIZATION -> {
                // User just granted Sheets permission - retry sync
                if (resultCode == RESULT_OK) {
                    Toast.makeText(this, R.string.sheets_permission_granted, Toast.LENGTH_SHORT).show()
                    performSync()
                }
            }
        }
    }

    private fun updateAccountUI() {
        val email = SheetsPreferences.getAccountEmail(this)
        if (email != null) {
            tvAccountStatus.text = getString(R.string.sheets_linked_as, email)
            btnPickAccount.visibility = android.view.View.GONE
            btnClearAccount.visibility = android.view.View.VISIBLE
            btnSyncNow.isEnabled = true
        } else {
            tvAccountStatus.text = getString(R.string.sheets_not_linked)
            btnPickAccount.visibility = android.view.View.VISIBLE
            btnClearAccount.visibility = android.view.View.GONE
            btnSyncNow.isEnabled = false
        }
    }

    private fun setupFrequencySpinner() {
        val options = arrayOf(
            getString(R.string.sheets_sync_disabled),
            getString(R.string.sheets_sync_daily),
            getString(R.string.sheets_sync_weekly)
        )
        spinnerFrequency.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        spinnerFrequency.setSelection(SheetsPreferences.getAutoSyncMode(this))
    }

    private fun setupYearSpinner() {
        val db = DatabaseAdapter(this)
        val years = mutableSetOf<Int>()
        try {
            val cursor = db.db().rawQuery(
                "SELECT min(datetime), max(datetime) FROM transactions WHERE is_template = 0",
                null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    val minMs = it.getLong(0)
                    val maxMs = it.getLong(1)
                    if (minMs > 0 && maxMs > 0) {
                        val cal = Calendar.getInstance()
                        cal.timeInMillis = minMs
                        val minYear = cal.get(Calendar.YEAR)
                        cal.timeInMillis = maxMs
                        val maxYear = cal.get(Calendar.YEAR)
                        for (y in minYear..maxYear) {
                            years.add(y)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Always ensure the current year is selectable
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        years.add(currentYear)

        yearsList = years.toList().sorted()
        val savedYear = SheetsPreferences.getSyncYear(this)
        val options = yearsList.map { "${it}年" }
        spinnerYear.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        val selectedIdx = yearsList.indexOf(savedYear)
        if (selectedIdx >= 0) {
            spinnerYear.setSelection(selectedIdx)
        } else {
            val currentIdx = yearsList.indexOf(currentYear)
            if (currentIdx >= 0) {
                spinnerYear.setSelection(currentIdx)
            }
        }
    }

    private fun loadPayees() {
        val db = DatabaseAdapter(this)
        val allPayees: List<Payee> = db.getAllPayeeList()
        val savedIds = SheetsPreferences.getSyncPayeeIds(this)

        llPayeeContainer.removeAllViews()
        payeeCheckBoxes.clear()

        for (payee in allPayees) {
            val cb = CheckBox(this).apply {
                text = payee.title
                isChecked = payee.id in savedIds
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 15f
                setPadding(0, 12, 0, 12)
            }
            llPayeeContainer.addView(cb)
            payeeCheckBoxes.add(Pair(cb, payee.id))
        }
    }

    private fun saveSettings() {
        val selectedIds = payeeCheckBoxes.filter { it.first.isChecked }.map { it.second }.toSet()
        SheetsPreferences.saveSyncPayeeIds(this, selectedIds)

        val mode = spinnerFrequency.selectedItemPosition
        SheetsPreferences.saveAutoSyncMode(this, mode)

        val yearPos = spinnerYear.selectedItemPosition
        if (yearPos in yearsList.indices) {
            SheetsPreferences.saveSyncYear(this, yearsList[yearPos])
        }

        SheetsPreferences.saveMonthlyBudget(this, etMonthlyBudget.text.toString().trim())
        SheetsSyncWorker.reschedule(this)

        Toast.makeText(this, R.string.sheets_settings_saved, Toast.LENGTH_SHORT).show()
    }

    private fun performSync() {
        val accountName = SheetsPreferences.getAccountEmail(this) ?: return
        val payeeIds = SheetsPreferences.getSyncPayeeIds(this)
        if (payeeIds.isEmpty()) {
            Toast.makeText(this, R.string.sheets_no_payees_selected, Toast.LENGTH_SHORT).show()
            return
        }

        @Suppress("DEPRECATION")
        val pd = ProgressDialog.show(this, null, getString(R.string.sheets_syncing), true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val credential = SheetsAuthHelper.buildCredential(this@GoogleSheetsSyncActivity, accountName)
                val db = DatabaseAdapter(this@GoogleSheetsSyncActivity)
                val exporter = TransactionExporter(this@GoogleSheetsSyncActivity)
                val sync = GoogleSheetsSync(credential)

                val yearPos = withContext(Dispatchers.Main) { spinnerYear.selectedItemPosition }
                val year = if (yearPos in yearsList.indices) yearsList[yearPos] else Calendar.getInstance().get(Calendar.YEAR)

                val budgetStr = SheetsPreferences.getMonthlyBudget(this@GoogleSheetsSyncActivity)
                val budget = budgetStr.toDoubleOrNull() ?: 0.0

                val existingId = SheetsPreferences.getSpreadsheetId(this@GoogleSheetsSyncActivity)
                val dataByMonth = exporter.exportByMonth(db, year, payeeIds)
                val totalRows = dataByMonth.values.sumOf { it.size }

                val url = if (dataByMonth.isNotEmpty()) {
                    val spreadsheetId = sync.getOrCreateSpreadsheet(year, existingId)
                    SheetsPreferences.saveSpreadsheetId(this@GoogleSheetsSyncActivity, spreadsheetId)
                    sync.syncYear(spreadsheetId, dataByMonth, budget)
                } else ""

                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    if (url.isNotEmpty()) {
                        Toast.makeText(
                            this@GoogleSheetsSyncActivity,
                            getString(R.string.sheets_sync_success, totalRows),
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this@GoogleSheetsSyncActivity, R.string.sheets_no_data, Toast.LENGTH_SHORT).show()
                    }
                }

            } catch (e: UserRecoverableAuthIOException) {
                // Sheets scope not yet consented — show system permission dialog
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    startActivityForResult(e.intent, RC_AUTHORIZATION)
                }
            } catch (e: com.google.android.gms.auth.UserRecoverableAuthException) {
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    e.intent?.let { intent ->
                        startActivityForResult(intent, RC_AUTHORIZATION)
                    } ?: run {
                        Toast.makeText(this@GoogleSheetsSyncActivity, e.message ?: "UserRecoverableAuthException", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    pd.dismiss()
                    val errorMsg = e.message ?: e.javaClass.simpleName
                    Toast.makeText(
                        this@GoogleSheetsSyncActivity,
                        getString(R.string.sheets_sync_error, errorMsg),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
