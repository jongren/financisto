package ru.orangesoftware.financisto.sheets

import android.content.Context
import android.database.Cursor
import ru.orangesoftware.financisto.db.DatabaseAdapter
import ru.orangesoftware.financisto.db.DatabaseHelper.BlotterColumns
import ru.orangesoftware.financisto.db.DatabaseHelper.V_BLOTTER_FLAT_SPLITS
import ru.orangesoftware.financisto.model.Currency
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Data class representing one row to be written to Google Sheets.
 */
data class SyncRow(
    val date: String,       // "2026/06/10"
    val type: String,       // "支出" or "收入"
    val category: String,   // leaf category name
    val firstLevelCategory: String, // first-level (parent) category name
    val amount: Double,     // negative for expense, positive for income (in TWD)
    val payee: String,      // payee name
    val note: String        // transaction note
)


/**
 * Reads transactions from the local DB and converts them to SyncRows,
 * applying the payee-based filter and currency conversion to TWD.
 */
class TransactionExporter(private val context: Context) {

    /**
     * Returns all SyncRows grouped by month (1-indexed) for the given year,
     * filtered to only include transactions whose payee is in [allowedPayeeIds].
     * Transfer transactions (is_transfer != 0) are excluded.
     */
    fun exportByMonth(
        db: DatabaseAdapter,
        year: Int,
        allowedPayeeIds: Set<Long>
    ): Map<Int, List<SyncRow>> {
        if (allowedPayeeIds.isEmpty()) return emptyMap()

        val placeholders = allowedPayeeIds.joinToString(",") { "?" }
        val payeeArgs = allowedPayeeIds.map { it.toString() }.toTypedArray()

        // Build time range for the year
        val cal = Calendar.getInstance()
        cal.set(year, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startMs = cal.timeInMillis
        cal.set(year, Calendar.DECEMBER, 31, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endMs = cal.timeInMillis

        val allCategoriesMap = db.getAllCategoriesMap()

        // Query blotter: non-transfer, non-template, within year, payee in allowed set
        val selection = """
            ${BlotterColumns.is_transfer} = 0
            AND ${BlotterColumns.datetime} >= ?
            AND ${BlotterColumns.datetime} <= ?
            AND ${BlotterColumns.payee_id} IN ($placeholders)
            AND ${BlotterColumns.is_template} = 0
        """.trimIndent()

        val args = arrayOf(startMs.toString(), endMs.toString()) + payeeArgs

        val rows = mutableMapOf<Int, MutableList<SyncRow>>()

        val cursor: Cursor = db.db().query(
            V_BLOTTER_FLAT_SPLITS,
            BlotterColumns.NORMAL_PROJECTION,
            selection,
            args,
            null, null,
            "${BlotterColumns.datetime} ASC"
        )

        val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
        val payeeMap = db.getAllPayeeByIdMap()

        cursor.use {
            while (it.moveToNext()) {
                val datetime = it.getLong(BlotterColumns.datetime.ordinal)
                val fromAmount = it.getLong(BlotterColumns.from_amount.ordinal)
                val categoryId = it.getLong(BlotterColumns.category_id.ordinal)
                val payeeId = it.getLong(BlotterColumns.payee_id.ordinal)
                val note = it.getString(BlotterColumns.note.ordinal) ?: ""
                val originalCurrencyId = it.getLong(BlotterColumns.original_currency_id.ordinal)
                val originalFromAmount = it.getLong(BlotterColumns.original_from_amount.ordinal)

                // Convert to TWD: if fromAmount is already in account currency use it directly.
                // original_currency_id > 0 means the account currency differs from transaction currency;
                // fromAmount already stores the converted value in account currency (TWD assumed).
                val amountInTwd: Double = fromAmount / 100.0

                val category = allCategoriesMap[categoryId]
                val leafCategoryName = category?.title ?: "無分類"

                // Resolve the first-level (top parent) category
                var current = category
                while (current?.parent != null) {
                    current = current.parent
                }
                val firstLevelCategoryName = current?.title ?: "無分類"

                val payeeName = payeeMap[payeeId]?.title ?: ""
                val type = if (fromAmount < 0) "支出" else "收入"

                val date = dateFormat.format(Date(datetime))

                val cal2 = Calendar.getInstance().apply { timeInMillis = datetime }
                val month = cal2.get(Calendar.MONTH) + 1  // 1-indexed

                val row = SyncRow(
                    date = date,
                    type = type,
                    category = leafCategoryName,
                    firstLevelCategory = firstLevelCategoryName,
                    amount = amountInTwd,
                    payee = payeeName,
                    note = note
                )
                rows.getOrPut(month) { mutableListOf() }.add(row)
            }
        }

        // Sort each month: by firstLevelCategory, then by category, then by date asc
        for (month in rows.keys) {
            rows[month] = rows[month]!!
                .sortedWith(compareBy({ it.firstLevelCategory }, { it.category }, { it.date }))
                .toMutableList()
        }

        return rows
    }
}
