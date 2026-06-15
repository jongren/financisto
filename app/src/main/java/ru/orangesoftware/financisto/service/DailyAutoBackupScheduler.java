/*
 * Copyright (c) 2011 Denis Solonenko.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 */

package ru.orangesoftware.financisto.service;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import ru.orangesoftware.financisto.utils.MyPreferences;

public class DailyAutoBackupScheduler {

    private final int hh;
    private final int mm;
    private final long now;

    public static void scheduleNextAutoBackup(Context context) {
        if (MyPreferences.isAutoBackupEnabled(context)) {
            int hhmm = MyPreferences.getAutoBackupTime(context);
            int hh = hhmm/100;
            int mm = hhmm - 100*hh;
            new DailyAutoBackupScheduler(hh, mm, System.currentTimeMillis()).scheduleBackup(context);
        } else {
            WorkManager.getInstance(context).cancelUniqueWork("DailyAutoBackup");
            Log.i("Financisto", "Auto-backup canceled via WorkManager");
        }
    }
    
    DailyAutoBackupScheduler(int hh, int mm, long now) {
        this.hh = hh;
        this.mm = mm;
        this.now = now;
    }

    private void scheduleBackup(Context context) {
        WorkManager workManager = WorkManager.getInstance(context);
        Date scheduledTime = getScheduledTime();
        long initialDelay = scheduledTime.getTime() - now;

        Constraints.Builder constraintsBuilder = new Constraints.Builder()
                .setRequiresBatteryNotLow(true);

        if (MyPreferences.isDropboxUploadAutoBackups(context) || MyPreferences.isGoogleDriveUploadAutoBackups(context)) {
            constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED);
        }

        PeriodicWorkRequest backupRequest = new PeriodicWorkRequest.Builder(AutoBackupWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(constraintsBuilder.build())
                .build();

        workManager.enqueueUniquePeriodicWork(
                "DailyAutoBackup",
                ExistingPeriodicWorkPolicy.REPLACE,
                backupRequest
        );
        Log.i("Financisto", "Next auto-backup scheduled at "+scheduledTime+" via WorkManager");
    }

    Date getScheduledTime() {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hh);
        c.set(Calendar.MINUTE, mm);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() < now) {
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        return c.getTime();
    }

}
