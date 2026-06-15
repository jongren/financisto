package ru.orangesoftware.financisto.sheets

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.sheets.v4.model.AddSheetRequest
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest
import com.google.api.services.sheets.v4.model.ClearValuesRequest
import com.google.api.services.sheets.v4.model.DeleteSheetRequest
import com.google.api.services.sheets.v4.model.Request
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.Spreadsheet
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import com.google.api.services.sheets.v4.model.ValueRange

/**
 * Core Google Sheets sync logic.
 * All methods must be called on a background thread.
 *
 * Spreadsheet structure:
 *   File name : "Financisto_{year}"
 *   Sheets    : "1月", "2月", ..., "12月"
 *   Columns   : 日期 | 類型 | 分類 | 金額 | 受款人 | 備註
 */
class GoogleSheetsSync(credential: GoogleAccountCredential) {

    companion object {
        private const val APP_NAME = "Financisto Family Sync"
        private val HEADER_ROW = listOf("日期", "類型", "分類", "金額", "受款人", "備註")

        val MONTH_NAMES = listOf(
            "1月", "2月", "3月", "4月", "5月", "6月",
            "7月", "8月", "9月", "10月", "11月", "12月"
        )
    }

    private val service: Sheets = Sheets.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    ).setApplicationName(APP_NAME).build()

    // -----------------------------------------------------------------------
    // Spreadsheet management
    // -----------------------------------------------------------------------

    /**
     * Finds an existing spreadsheet named "Financisto_{year}" or creates a new one.
     * Returns the spreadsheet ID.
     */
    fun getOrCreateSpreadsheet(year: Int, existingId: String?): String {
        // Try existing saved ID first
        if (!existingId.isNullOrBlank()) {
            try {
                service.spreadsheets().get(existingId).execute()
                return existingId          // still accessible
            } catch (_: Exception) { /* fall through to create */ }
        }
        // Create a new spreadsheet
        val spreadsheet = Spreadsheet().apply {
            properties = SpreadsheetProperties().apply {
                title = "Financisto_$year"
            }
        }
        val created = service.spreadsheets().create(spreadsheet).execute()
        return created.spreadsheetId
    }

    // -----------------------------------------------------------------------
    // Main sync entry point
    // -----------------------------------------------------------------------

    /**
     * Performs a full sync: for all 12 months,
     * clears the sheet and rewrites all rows. Returns the spreadsheet URL.
     */
    fun syncYear(
        spreadsheetId: String,
        dataByMonth: Map<Int, List<SyncRow>>,
        budget: Double
    ): String {
        val spreadsheet = service.spreadsheets().get(spreadsheetId).execute()
        val existingSheets = spreadsheet.sheets ?: emptyList<Sheet>()
        val existingByName = existingSheets.associateBy { it.properties.title }

        for (month in 1..12) {
            val sheetName = MONTH_NAMES[month - 1]
            ensureSheet(spreadsheetId, sheetName, existingByName)
            val rows = dataByMonth[month] ?: emptyList()
            writeMonth(spreadsheetId, sheetName, rows, budget, month)
        }

        return "https://docs.google.com/spreadsheets/d/$spreadsheetId"
    }

    // -----------------------------------------------------------------------
    // Sheet helpers
    // -----------------------------------------------------------------------

    /** Returns the sheetId of the named sheet, creating it if necessary. */
    private fun ensureSheet(
        spreadsheetId: String,
        sheetName: String,
        existingByName: Map<String, Sheet>
    ): Int {
        existingByName[sheetName]?.let { return it.properties.sheetId }

        // Add new sheet
        val addRequest = Request().apply {
            addSheet = AddSheetRequest().apply {
                properties = SheetProperties().apply { title = sheetName }
            }
        }
        val response = service.spreadsheets().batchUpdate(
            spreadsheetId,
            BatchUpdateSpreadsheetRequest().apply { requests = listOf(addRequest) }
        ).execute()
        return response.replies[0].addSheet.properties.sheetId
    }

    /**
     * Clears the given sheet and writes header + data rows + category subtotal rows.
     */
    private fun writeMonth(
        spreadsheetId: String,
        sheetName: String,
        rows: List<SyncRow>,
        budget: Double,
        month: Int
    ) {
        // 1. Clear existing content
        service.spreadsheets().values()
            .clear(spreadsheetId, sheetName, ClearValuesRequest())
            .execute()

        // 2. Build all value rows
        val values = mutableListOf<List<Any>>()
        values.add(HEADER_ROW)

        // Group rows by first-level category, preserving sorted order (already sorted by TransactionExporter)
        val grouped = rows.groupBy { it.firstLevelCategory }
        var rowIndex = 2  // 1-based; row 1 is header

        val expenseSubtotalCells = mutableListOf<String>()

        for ((firstLevelCategory, catRows) in grouped) {
            val startRow = rowIndex
            for (row in catRows) {
                values.add(
                    listOf(row.date, row.type, row.category, row.amount, row.payee, row.note)
                )
                rowIndex++
            }
            val endRow = rowIndex - 1
            val subtotalRow = rowIndex
            // SUM formula for this category block, placed right after the category transactions
            values.add(
                listOf(
                    "",
                    "",
                    "【$firstLevelCategory 小計】",
                    "=SUM(D${startRow}:D${endRow})",
                    "",
                    ""
                )
            )
            rowIndex++

            // Check if this first-level category is an expense category (has any negative transactions)
            val isExpense = catRows.any { it.amount < 0 }
            if (isExpense) {
                expenseSubtotalCells.add("D$subtotalRow")
            }

            // Add blank separator row
            values.add(listOf())
            rowIndex++
        }

        // Remove the trailing blank separator row if present
        if (values.isNotEmpty() && values.last().isEmpty()) {
            values.removeAt(values.size - 1)
        }

        // Add blank separator row before summary block
        values.add(listOf())

        // C Column label, D Column value/formula
        // 1. 本月支出統計 (Row values.size + 1)
        val expensesRow = values.size + 1
        val expensesFormula = if (expenseSubtotalCells.isNotEmpty()) {
            "=-(${expenseSubtotalCells.joinToString("+")})"
        } else {
            0.0
        }
        values.add(listOf("", "", "本月支出統計", expensesFormula, "", ""))

        // 2. 每月預算 (Row values.size + 1)
        val budgetRow = values.size + 1
        values.add(listOf("", "", "每月預算", budget, "", ""))

        if (month == 1) {
            // 3. 累計結餘 (Row values.size + 1)
            values.add(listOf("", "", "累計結餘", "=D$budgetRow-D$expensesRow", "", ""))
        } else {
            // 3. 上月結餘 (Row values.size + 1)
            val prevMonthName = MONTH_NAMES[month - 2]
            val prevBalanceRow = values.size + 1
            val prevBalanceFormula = "=SUMIF('$prevMonthName'!C:C, \"累計結餘\", '$prevMonthName'!D:D)"
            values.add(listOf("", "", "上月結餘", prevBalanceFormula, "", ""))

            // 4. 累計結餘 (Row values.size + 1)
            values.add(listOf("", "", "累計結餘", "=D$budgetRow-D$expensesRow+D$prevBalanceRow", "", ""))
        }

        // 3. Write all at once
        val body = ValueRange().apply { setValues(values) }
        service.spreadsheets().values()
            .update(spreadsheetId, "$sheetName!A1", body)
            .setValueInputOption("USER_ENTERED")
            .execute()
    }
}
