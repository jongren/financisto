package ru.orangesoftware.financisto.activity

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import ru.orangesoftware.financisto.R
import ru.orangesoftware.financisto.utils.MyPreferences
import ru.orangesoftware.financisto.utils.Utils

class AboutActivity : AppCompatActivity() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(MyPreferences.switchLocale(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                AboutScreen()
            }
        }
    }

    @Composable
    fun AboutScreen() {
        var selectedTabIndex by remember { mutableStateOf(0) }
        val tabs = remember {
            listOf(
                TabItem(R.string.whats_new, "whatsnew", true),
                TabItem(R.string.privacy_policy, "privacy", true),
                TabItem(R.string.license, "gpl-2.0-standalone", true),
                TabItem(R.string.about, "about", true)
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Financisto Classic",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = getAppVersion(this@AboutActivity),
                        fontSize = 12.sp,
                        color = Color(0xFF9E9E9E)
                    )
                }
            }

            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color.White
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                stringResource(id = tab.titleRes),
                                color = if (selectedTabIndex == index) Color(0xFF3F51B5) else Color(0xFF9E9E9E)
                            )
                        }
                    )
                }
            }

            val currentTab = tabs[selectedTabIndex]
            val url = if (currentTab.isFile) {
                if (currentTab.content == "privacy" && java.util.Locale.getDefault().language == "zh") {
                    "file:///android_asset/privacy_zh.htm"
                } else {
                    "file:///android_asset/${currentTab.content}.htm"
                }
            } else {
                currentTab.content
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewClient = WebViewClient()
                    }
                },
                update = { webView ->
                    webView.loadUrl(url)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    data class TabItem(val titleRes: Int, val content: String, val isFile: Boolean)

    companion object {
        fun getAppVersion(context: Context): String {
            return try {
                val info = Utils.getPackageInfo(context)
                "v. " + info.versionName
            } catch (e: PackageManager.NameNotFoundException) {
                ""
            }
        }
    }
}
