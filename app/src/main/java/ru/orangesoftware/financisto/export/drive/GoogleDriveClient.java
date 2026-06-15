package ru.orangesoftware.financisto.export.drive;

import android.content.Context;
import android.util.Log;

import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.client.http.InputStreamContent;

import org.androidannotations.annotations.AfterInject;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EBean;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import ru.orangesoftware.financisto.R;
import ru.orangesoftware.financisto.backup.DatabaseExport;
import ru.orangesoftware.financisto.backup.DatabaseImport;
import ru.orangesoftware.financisto.bus.GreenRobotBus;
import ru.orangesoftware.financisto.db.DatabaseAdapter;
import ru.orangesoftware.financisto.export.Export;
import ru.orangesoftware.financisto.export.ImportExportException;
import ru.orangesoftware.financisto.utils.MyPreferences;

@EBean(scope = EBean.Scope.Singleton)
public class GoogleDriveClient {

    private static final String TAG = GoogleDriveClient.class.getSimpleName();

    private final Context context;

    @Bean
    GreenRobotBus bus;

    @Bean
    DatabaseAdapter db;

    GoogleDriveClient(Context context) {
        this.context = context.getApplicationContext();
    }

    @AfterInject
    public void init() {
        bus.register(this);
    }

    private Drive getOrCreateDriveService() throws ImportExportException {
        String googleDriveAccount = MyPreferences.getGoogleDriveAccount(context);
        if (googleDriveAccount == null || googleDriveAccount.trim().isEmpty()) {
            throw new ImportExportException(R.string.google_drive_account_required);
        }
        GoogleAccountCredential credential = GoogleAccountCredential.usingOAuth2(
                context, Collections.singletonList(DriveScopes.DRIVE_FILE));
        credential.setSelectedAccountName(googleDriveAccount);

        return new Drive.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName("Financisto")
                .build();
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void doBackup(DoDriveBackup event) {
        DatabaseExport export = new DatabaseExport(context, db.db(), true);
        try {
            String targetFolderName = getDriveFolderName();
            Drive service = getOrCreateDriveService();
            String folderId = getOrCreateFolderId(service, targetFolderName);

            String fileName = export.generateFilename();
            byte[] bytes = export.generateBackupBytes();

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            fileMetadata.setMimeType(Export.BACKUP_MIME_TYPE);
            fileMetadata.setParents(Collections.singletonList(folderId));

            InputStreamContent mediaContent = new InputStreamContent(
                    Export.BACKUP_MIME_TYPE,
                    new ByteArrayInputStream(bytes)
            );

            File file = service.files().create(fileMetadata, mediaContent)
                    .setFields("id, name")
                    .setSupportsAllDrives(true)
                    .execute();

            if (file != null && file.getId() != null) {
                bus.post(new DriveBackupSuccess(fileName));
            } else {
                bus.post(new DriveBackupError(context.getString(R.string.google_drive_connection_failed)));
            }
        } catch (UserRecoverableAuthIOException e) {
            bus.post(new DriveNeedAuth(e.getIntent()));
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void listFiles(DoDriveListFiles event) {
        try {
            String targetFolderName = getDriveFolderName();
            Drive service = getOrCreateDriveService();
            String folderId = getOrCreateFolderId(service, targetFolderName);

            String query = "'" + folderId + "' in parents and mimeType = '" 
                    + Export.BACKUP_MIME_TYPE + "' and trashed = false";
            FileList result = service.files().list()
                    .setQ(query)
                    .setSpaces("drive")
                    .setFields("files(id, name, createdTime)")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .execute();

            List<File> files = result.getFiles();
            List<DriveFileInfo> driveFiles = new ArrayList<>();
            if (files != null) {
                for (File file : files) {
                    String title = file.getName();
                    if (!title.endsWith(".backup")) continue;
                    long timeMs = file.getCreatedTime() != null ? file.getCreatedTime().getValue() : System.currentTimeMillis();
                    driveFiles.add(new DriveFileInfo(file.getId(), title, new Date(timeMs)));
                }
            }
            Collections.sort(driveFiles);
            bus.post(new DriveFileList(driveFiles));
        } catch (UserRecoverableAuthIOException e) {
            bus.post(new DriveNeedAuth(e.getIntent()));
        } catch (Exception e) {
            handleError(e);
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    public void doRestore(DoDriveRestore event) {
        try {
            Drive service = getOrCreateDriveService();
            String fileId = event.selectedDriveFile.driveId;
            InputStream inputStream = service.files().get(fileId)
                    .setSupportsAllDrives(true)
                    .executeMediaAsInputStream();
            try {
                DatabaseImport.createFromGoogleDriveBackup(context, db, inputStream).importDatabase();
                bus.post(new DriveRestoreSuccess());
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (UserRecoverableAuthIOException e) {
            bus.post(new DriveNeedAuth(e.getIntent()));
        } catch (Exception e) {
            handleError(e);
        }
    }

    private String getOrCreateFolderId(Drive service, String folderName) throws Exception {
        String query = "mimeType = 'application/vnd.google-apps.folder' and name = '" 
                + folderName.replace("'", "\\'") + "' and trashed = false";
        FileList result = service.files().list()
                .setQ(query)
                .setSpaces("drive")
                .setFields("files(id, name, mimeType)")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .execute();
        List<File> files = result.getFiles();
        if (files != null && !files.isEmpty()) {
            return files.get(0).getId();
        }
        // Folder does not exist, create it
        File folderMetadata = new File();
        folderMetadata.setName(folderName);
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        File folder = service.files().create(folderMetadata)
                .setFields("id, name, mimeType")
                .setSupportsAllDrives(true)
                .execute();
        return folder.getId();
    }

    private String getDriveFolderName() throws ImportExportException {
        String folder = MyPreferences.getBackupFolder(context);
        if (folder == null || folder.trim().isEmpty()) {
            throw new ImportExportException(R.string.gdocs_folder_not_configured);
        }
        return folder;
    }

    private void handleError(Exception e) {
        Log.e(TAG, "Google Drive operation failed", e);
        if (e instanceof ImportExportException) {
            ImportExportException importExportException = (ImportExportException) e;
            bus.post(new DriveBackupError(context.getString(importExportException.errorResId)));
        } else {
            bus.post(new DriveBackupError(e.getMessage()));
        }
    }

    public void uploadFile(java.io.File file) throws ImportExportException {
        try {
            String targetFolderName = getDriveFolderName();
            Drive service = getOrCreateDriveService();
            String folderId = getOrCreateFolderId(service, targetFolderName);

            File fileMetadata = new File();
            fileMetadata.setName(file.getName());
            fileMetadata.setMimeType(Export.BACKUP_MIME_TYPE);
            fileMetadata.setParents(Collections.singletonList(folderId));

            InputStreamContent mediaContent = new InputStreamContent(
                    Export.BACKUP_MIME_TYPE,
                    new java.io.FileInputStream(file)
            );

            service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .setSupportsAllDrives(true)
                    .execute();
        } catch (Exception e) {
            throw new ImportExportException(R.string.google_drive_connection_failed, e);
        }
    }

}
