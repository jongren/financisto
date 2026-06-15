# Financisto Classic

This is a modern hotfix fork of the original open-source personal finance tracker **Financisto** for Android.

## Key Upgrades & Hotfixes in this Fork

- **Android 14+ Compatibility**: Target SDK updated to 34 with full background services and permission compliance.
- **Modernized Main Screen**: Restructured the main dashboard from legacy TabActivity to Jetpack Compose with Fragment tabs.
- **Dropbox PKCE OAuth2 Flow**: Upgraded to Dropbox SDK 6.1.0 with secure PKCE authorization, TLS 1.3, and restricted scopes.
- **Google Drive Integration**: Migrated backup/restore logic to Google Drive REST API v3.
- **Google Sheets Family Sync**: Implemented transactions sync to Google Sheets, including monthly budgets and payee-based filtering.
- **WorkManager Auto-Backups**: Replaced legacy AlarmManager with Android WorkManager for reliable periodic background database backups.
- **UI & UX Polish**: Upgraded legacy dialogs to Material/AppCompat templates, fixed text color/truncation issues, and improved overall layout scaling.
- **Localization & Category Selection**: Fully synchronized EN, zh-TW, and zh-CN translations; resolved `<SPLIT_CATEGORY>`/`<NO_CATEGORY>` displays and category selector back-button navigation.

## License

See [License](license.txt)
