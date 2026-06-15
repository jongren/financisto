package ru.orangesoftware.financisto.service;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Date;

import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.backup.DatabaseExport;
import ru.orangesoftware.financisto.export.Export;
import ru.orangesoftware.financisto.utils.MyPreferences;

public class AutoBackupWorker extends Worker {

    private static final String TAG = "AutoBackupWorker";

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        if (!MyPreferences.isAutoBackupEnabled(context)) {
            return Result.success();
        }

        DatabaseAdapter db = new DatabaseAdapter(context);
        try {
            db.open();
            long t0 = System.currentTimeMillis();
            Log.e(TAG, "Auto-backup started at " + new Date());
            DatabaseExport export = new DatabaseExport(context, db.db(), true);
            String fileName = export.export();
            boolean successful = true;

            if (MyPreferences.isDropboxUploadAutoBackups(context)) {
                try {
                    Export.uploadBackupFileToDropbox(context, fileName);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to upload auto-backup to Dropbox", e);
                    MyPreferences.notifyAutobackupFailed(context, e);
                    successful = false;
                }
            }

            if (MyPreferences.isGoogleDriveUploadAutoBackups(context)) {
                try {
                    Export.uploadBackupFileToGoogleDrive(context, fileName);
                } catch (Exception e) {
                    Log.e(TAG, "Unable to upload auto-backup to Google Drive", e);
                    MyPreferences.notifyAutobackupFailed(context, e);
                    successful = false;
                }
            }

            Log.e(TAG, "Auto-backup completed in " + (System.currentTimeMillis() - t0) + "ms");
            if (successful) {
                MyPreferences.notifyAutobackupSucceeded(context);
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Auto-backup unsuccessful", e);
            MyPreferences.notifyAutobackupFailed(context, e);
            return Result.failure();
        } finally {
            db.close();
        }
    }
}
