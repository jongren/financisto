package ru.orangesoftware.financisto.activity

import android.app.ProgressDialog
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import kotlinx.coroutines.launch
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.collectLatest
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.bus.RefreshCurrentTab
import ru.orangesoftware.financisto.bus.SwitchToBlotterTabEvent
import ru.orangesoftware.financisto.bus.SwitchToMenuTabEvent
import ru.orangesoftware.financisto.export.BackupImportFromUriTask
import ru.orangesoftware.financisto.export.csv.CsvExportOptions
import ru.orangesoftware.financisto.export.csv.CsvImportOptions
import ru.orangesoftware.financisto.export.qif.QifExportOptions
import ru.orangesoftware.financisto.export.qif.QifImportOptions
import ru.orangesoftware.financisto.service.DailyAutoBackupScheduler.scheduleNextAutoBackup
import ru.orangesoftware.financisto.utils.PinProtection
import android.widget.Toast
import ru.orangesoftware.financisto.export.drive.*
import ru.orangesoftware.financisto.export.dropbox.*
import ru.orangesoftware.financisto.utils.IntegrityCheckAutobackup
import java.util.concurrent.TimeUnit

class MainCompatActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Manually set view tree owners to support ComposeView under older AppCompat versions
        window.decorView.setViewTreeLifecycleOwner(this)
        window.decorView.setViewTreeViewModelStoreOwner(this)
        window.decorView.setViewTreeSavedStateRegistryOwner(this)

        setContentView(R.layout.activity_main_compose)

        IntegrityCheckTask(this).execute(IntegrityCheckAutobackup(this, TimeUnit.DAYS.toMillis(7)))

        findViewById<ComposeView>(R.id.compose_tab_bar).setContent {
            MaterialTheme {
                val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
                AnimatedTopTabBar(
                    selectedTab = currentTab,
                    onTabSelected = { tab ->
                        viewModel.selectTab(tab)
                    }
                )
            }
        }

        // Hook up system back press adaptation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTab = viewModel.currentTab.value
                if (currentTab == Tab.BLOTTER) {
                    val f = supportFragmentManager.findFragmentByTag(Tab.BLOTTER.tag)
                    if (f is BlotterFragment && f.onBackPressed()) {
                        return
                    }
                }
                if (currentTab != Tab.ACCOUNTS) {
                    viewModel.selectTab(Tab.ACCOUNTS)
                } else {
                    moveTaskToBack(true)
                }
            }
        })

        // Collect navigation states to update fragment views
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.currentTab.collectLatest { tab ->
                    switchFragment(tab)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PinProtection.unlock(this)
        EventBus.getDefault().register(this)
    }

    override fun onPause() {
        super.onPause()
        PinProtection.lock(this)
        EventBus.getDefault().unregister(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        PinProtection.immediateLock(this)
    }

    private fun switchFragment(tab: Tab) {
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()

        val tags = Tab.values().map { it.tag }
        for (t in tags) {
            val f = fm.findFragmentByTag(t)
            if (f != null) {
                if (t == tab.tag) {
                    transaction.show(f)
                } else {
                    transaction.hide(f)
                }
            }
        }

        var fragment = fm.findFragmentByTag(tab.tag)
        if (fragment == null) {
            fragment = when (tab) {
                Tab.ACCOUNTS -> AccountsFragment()
                Tab.BLOTTER -> BlotterFragment()
                Tab.BUDGETS -> BudgetsFragment()
                Tab.REPORTS -> ReportsFragment()
                Tab.MENU -> MenuFragment()
            }
            transaction.add(R.id.fragment_container, fragment, tab.tag)
        } else {
            if (fragment is AccountsFragment) {
                fragment.refresh()
            } else if (fragment is BlotterFragment) {
                fragment.refresh()
            } else if (fragment is BudgetsFragment) {
                fragment.refresh()
            }
        }

        transaction.commitNowAllowingStateLoss()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onRefreshCurrentTab(e: RefreshCurrentTab) {
        refreshCurrentTab()
    }

    private fun refreshCurrentTab() {
        val tag = viewModel.currentTab.value.tag
        val f = supportFragmentManager.findFragmentByTag(tag)
        if (f is AccountsFragment) {
            f.refresh()
        } else if (f is BlotterFragment) {
            f.refresh()
        } else if (f is BudgetsFragment) {
            f.refresh()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwitchToBlotter(e: SwitchToBlotterTabEvent) {
        viewModel.selectTab(Tab.BLOTTER)
        switchFragment(Tab.BLOTTER)
        val f = supportFragmentManager.findFragmentByTag(Tab.BLOTTER.tag)
        if (f is BlotterFragment) {
            f.showAccount(e.accountId, e.title)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSwitchToMenu(e: SwitchToMenuTabEvent) {
        viewModel.selectTab(Tab.MENU)
        switchFragment(Tab.MENU)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MenuListItem.ACTIVITY_SAF_RESTORE) {
            if (resultCode == RESULT_OK && data != null) {
                val uri = data.getData()
                val flags = data.getFlags() and (android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                try {
                    contentResolver.takePersistableUriPermission(uri!!, flags)
                } catch (ignored: Exception) {
                }
                val pd = ProgressDialog.show(this, null, getString(R.string.restore_database_inprogress), true)
                BackupImportFromUriTask(this, pd, uri).execute()
            }
        } else if (requestCode == RESOLVE_CONNECTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, R.string.google_drive_connection_resolved, Toast.LENGTH_LONG).show()
                EventBus.getDefault().post(DoDriveBackup())
            }
        } else if (requestCode == MenuListItem.ACTIVITY_CSV_EXPORT) {
            if (resultCode == RESULT_OK && data != null) {
                val options = CsvExportOptions.fromIntent(data)
                MenuListItem.doCsvExport(this, options)
            }
        } else if (requestCode == MenuListItem.ACTIVITY_QIF_EXPORT) {
            if (resultCode == RESULT_OK && data != null) {
                val options = QifExportOptions.fromIntent(data)
                MenuListItem.doQifExport(this, options)
            }
        } else if (requestCode == MenuListItem.ACTIVITY_CSV_IMPORT) {
            if (resultCode == RESULT_OK && data != null) {
                val options = CsvImportOptions.fromIntent(data)
                MenuListItem.doCsvImport(this, options)
            }
        } else if (requestCode == MenuListItem.ACTIVITY_QIF_IMPORT) {
            if (resultCode == RESULT_OK && data != null) {
                val options = QifImportOptions.fromIntent(data)
                MenuListItem.doQifImport(this, options)
            }
        } else if (requestCode == MenuListItem.ACTIVITY_CHANGE_PREFERENCES) {
            scheduleNextAutoBackup(this)
        }
    }

    private val RESOLVE_CONNECTION_REQUEST_CODE = 1
    private var progressDialog: ProgressDialog? = null

    private fun dismissProgressDialog() {
        progressDialog?.let {
            if (it.isShowing) {
                it.dismiss()
            }
        }
        progressDialog = null
    }

    // Google Drive events
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doGoogleDriveBackup(e: MenuListActivity.StartDriveBackup) {
        progressDialog = ProgressDialog.show(this, null, getString(R.string.backup_database_gdocs_inprogress), true)
        EventBus.getDefault().post(DoDriveBackup())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doGoogleDriveRestore(e: MenuListActivity.StartDriveRestore) {
        progressDialog = ProgressDialog.show(this, null, getString(R.string.google_drive_loading_files), true)
        EventBus.getDefault().post(DoDriveListFiles())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveList(event: DriveFileList) {
        dismissProgressDialog()
        val files = event.files
        val names = Array(files.size) { i -> files[i].title }
        val selectedDriveFile = arrayOfNulls<DriveFileInfo>(1)
        val titleView = android.widget.TextView(this).apply {
            setText(R.string.restore_database_online_google_drive)
            setBackgroundColor(resources.getColor(R.color.colorPrimary))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val p = (resources.displayMetrics.density * 16).toInt()
            setPadding(p, p, p, p)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setPositiveButton(R.string.restore) { _, _ ->
                selectedDriveFile[0]?.let { file ->
                    progressDialog = ProgressDialog.show(this, null, getString(R.string.google_drive_restore_in_progress), true)
                    EventBus.getDefault().post(DoDriveRestore(file))
                }
            }
            .setSingleChoiceItems(names, -1) { _, which ->
                if (which in files.indices) {
                    selectedDriveFile[0] = files[which]
                }
            }
            .show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveNeedAuth(event: DriveNeedAuth) {
        dismissProgressDialog()
        try {
            startActivityForResult(event.intent, RESOLVE_CONNECTION_REQUEST_CODE)
        } catch (e: Exception) {
            onDriveBackupError(DriveBackupError(e.message))
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveBackupSuccess(event: DriveBackupSuccess) {
        dismissProgressDialog()
        Toast.makeText(this, getString(R.string.google_drive_backup_success, event.fileName), Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveRestoreSuccess(event: DriveRestoreSuccess) {
        dismissProgressDialog()
        Toast.makeText(this, R.string.restore_database_success, Toast.LENGTH_LONG).show()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onDriveBackupError(event: DriveBackupError) {
        dismissProgressDialog()
        Toast.makeText(this, getString(R.string.google_drive_connection_failed, event.message), Toast.LENGTH_LONG).show()
    }

    // Dropbox events
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doDropboxBackup(e: MenuListActivity.StartDropboxBackup) {
        val d = ProgressDialog.show(this, null, getString(R.string.backup_database_dropbox_inprogress), true)
        DropboxBackupTask(this, d).execute()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doDropboxRestore(e: MenuListActivity.StartDropboxRestore) {
        val d = ProgressDialog.show(this, null, getString(R.string.dropbox_loading_files), true)
        DropboxListFilesTask(this, d).execute()
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun doImportFromDropbox(event: DropboxFileList) {
        val backupFiles = event.files ?: return
        val selectedDropboxFile = arrayOfNulls<String>(1)
        val titleView = android.widget.TextView(this).apply {
            setText(R.string.restore_database_online_dropbox)
            setBackgroundColor(resources.getColor(R.color.colorPrimary))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val p = (resources.displayMetrics.density * 16).toInt()
            setPadding(p, p, p, p)
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setCustomTitle(titleView)
            .setPositiveButton(R.string.restore) { _, _ ->
                selectedDropboxFile[0]?.let { file ->
                    val d = ProgressDialog.show(this, null, getString(R.string.restore_database_inprogress_dropbox), true)
                    DropboxRestoreTask(this, d, file).execute()
                }
            }
            .setSingleChoiceItems(backupFiles, -1) { _, which ->
                if (which in backupFiles.indices) {
                    selectedDropboxFile[0] = backupFiles[which]
                }
            }
            .show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedTopTabBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 8.dp
    ) {
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFF3F51B5),
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                    color = Color(0xFF3F51B5),
                    height = 3.dp
                )
            },
            divider = {
                Spacer(modifier = Modifier.height(1.dp).background(Color(0xFF2A2A2A)))
            }
        ) {
            Tab.values().forEach { tab ->
                val isSelected = tab == selectedTab
                val scale by animateFloatAsState(if (isSelected) 1.1f else 1.0f)
                val color by animateColorAsState(if (isSelected) Color(0xFF3F51B5) else Color(0xFF9E9E9E))

                Tab(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            painter = painterResource(id = if (isSelected) tab.selectedIconRes else tab.unselectedIconRes),
                            contentDescription = stringResource(id = tab.titleRes),
                            tint = color,
                            modifier = Modifier
                                .size(28.dp)
                                .graphicsLayer(scaleX = scale, scaleY = scale)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(id = tab.titleRes),
                            color = color,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
