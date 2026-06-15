package ru.orangesoftware.financisto.sheets

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.google.android.gms.common.AccountPicker
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.sheets.v4.SheetsScopes

/**
 * Handles Google account selection using the phone's existing Google accounts
 * (via AccountPicker). No web OAuth2 flow or google-services.json needed.
 *
 * First-time Sheets API call will show a system consent dialog if needed.
 */
object SheetsAuthHelper {

    const val RC_ACCOUNT_PICKER = 9002

    /**
     * Returns an Intent to show the system account picker dialog,
     * letting the user select from Google accounts already on the device.
     */
    fun getAccountPickerIntent(): Intent =
        AccountPicker.newChooseAccountIntent(
            AccountPicker.AccountChooserOptions.Builder()
                .setAllowableAccountsTypes(listOf("com.google"))
                .setAlwaysShowAccountPicker(false)
                .build()
        )

    /**
     * Parses the account picker result and returns the chosen account name (email).
     * Returns null on cancellation.
     */
    fun handleAccountPickerResult(data: Intent?): String? =
        data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)

    /**
     * Builds a GoogleAccountCredential using the saved account name.
     * Tokens are obtained silently from AccountManager without any UI.
     * If Sheets scope is not yet consented, caller must catch
     * UserRecoverableAuthIOException and launch e.intent with RC_AUTHORIZATION.
     */
    fun buildCredential(context: Context, accountName: String): GoogleAccountCredential =
        GoogleAccountCredential.usingOAuth2(
            context,
            listOf(SheetsScopes.SPREADSHEETS)
        ).apply {
            selectedAccountName = accountName
        }

    /**
     * Returns all Google account names already on the device.
     * Useful to skip the picker if there is only one account.
     */
    fun getAvailableAccounts(context: Context): List<String> =
        AccountManager.get(context)
            .getAccountsByType("com.google")
            .map { it.name }
}
