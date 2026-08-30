package com.atlasreader

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.atlasreader.core.importer.ImportCoordinator
import com.atlasreader.core.importer.ImportRequest
import com.atlasreader.ui.AtlasApp
import com.atlasreader.ui.theme.AtlasReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host. Edge-to-edge, adaptive navigation, and the import
 * entry points: SAF picker results, VIEW/SEND share intents.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var importCoordinator: ImportCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            AtlasReaderTheme {
                AtlasApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    val name = intent.getStringExtra(Intent.EXTRA_TITLE) ?: uri.lastPathSegment
                    importCoordinator.import(
                        listOf(
                            ImportRequest(
                                uri = uri,
                                displayName = name,
                                mimeType = intent.type,
                            )
                        )
                    )
                }
            }
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                uri?.let {
                    importCoordinator.import(
                        listOf(ImportRequest(uri = it, mimeType = intent.type))
                    )
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                uris?.let { list ->
                    importCoordinator.import(list.map { ImportRequest(uri = it, mimeType = intent.type) })
                }
            }
        }
    }
}
