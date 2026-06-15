package ru.orangesoftware.financisto.export;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.backup.DatabaseImport;
import ru.orangesoftware.financisto.db.DatabaseAdapter;

public class BackupImportFromUriTask extends ImportExportAsyncTask {

    private final Uri uri;

    public BackupImportFromUriTask(final Activity activity, ProgressDialog dialog, Uri uri) {
        super(activity, dialog);
        this.uri = uri;
    }

    @Override
    protected Object work(Context context, DatabaseAdapter db, String... params) throws Exception {
        DatabaseImport.createFromContentUri(context, db, uri).importDatabase();
        return true;
    }

    @Override
    protected String getSuccessMessage(Object result) {
        return context.getString(R.string.restore_database_success);
    }
}
