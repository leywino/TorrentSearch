package com.prajwalch.torrentsearch

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prajwalch.torrentsearch.data.DarkTheme
import com.prajwalch.torrentsearch.data.SettingsRepository
import com.prajwalch.torrentsearch.data.TorrentsRepository
import com.prajwalch.torrentsearch.models.MagnetUri
import com.prajwalch.torrentsearch.network.HttpClient
import com.prajwalch.torrentsearch.ui.TorrentSearchApp
import com.prajwalch.torrentsearch.ui.theme.TorrentSearchTheme
import com.prajwalch.torrentsearch.ui.viewmodel.SearchViewModel
import com.prajwalch.torrentsearch.ui.viewmodel.SearchViewModelFactory
import com.prajwalch.torrentsearch.ui.viewmodel.SettingsViewModel
import com.prajwalch.torrentsearch.ui.viewmodel.SettingsViewModelFactory

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

class MainActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(dataStore = settingsDataStore)
    }

    private val searchViewModel: SearchViewModel by viewModels {
        val torrentsRepository = TorrentsRepository(httpClient = HttpClient)

        SearchViewModelFactory(
            settingsRepository = settingsRepository,
            torrentsRepository = torrentsRepository,
        )
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(settingsRepository = settingsRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            val appearanceSettings by settingsViewModel.appearanceSettings.collectAsStateWithLifecycle()

            val darkTheme = when (appearanceSettings.darkTheme) {
                DarkTheme.On -> true
                DarkTheme.Off -> false
                DarkTheme.FollowSystem -> isSystemInDarkTheme()
            }

            TorrentSearchTheme(
                darkTheme = darkTheme,
                dynamicColor = appearanceSettings.enableDynamicTheme,
            ) {
                TorrentSearchApp(
                    searchViewModel = searchViewModel,
                    settingsViewModel = settingsViewModel,
                    onDownloadTorrent = ::downloadTorrentViaClient,
                    onShareMagnetLink = ::shareMagnetLink,
                    onOpenDescriptionPage = ::openDescriptionPage,
                    onShareDescriptionPageUrl = ::shareDescriptionPageUrl,
                )
            }
        }
    }

    /**
     * Starts the available torrent client for downloading the given torrent.
     *
     * @return `true` if the client starts successfully, `false` otherwise.
     */
    private fun downloadTorrentViaClient(magnetUri: MagnetUri): Boolean {
        val torrentClientOpenIntent = Intent(Intent.ACTION_VIEW, magnetUri.toUri())

        return try {
            startActivity(torrentClientOpenIntent)
            true
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "Torrent client launch failed. (Activity not found)")
            false
        }
    }

    /** Starts the application chooser to share magnet uri with. */
    private fun shareMagnetLink(magnetUri: MagnetUri) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, magnetUri)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)

        try {
            startActivity(shareIntent)
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "Magnet uri share intent launch failed. (Activity not found)")
        }
    }

    /** Opens a description page in a default browser. */
    private fun openDescriptionPage(url: String) {
        val openPageIntent = Intent(Intent.ACTION_VIEW, url.toUri())

        try {
            startActivity(openPageIntent)
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "Failed to open description page. (Activity not found)")
        }
    }

    /** Starts the application chooser to share url with. */
    private fun shareDescriptionPageUrl(url: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/html"
        }
        val shareIntent = Intent.createChooser(sendIntent, null)

        try {
            startActivity(shareIntent)
        } catch (_: ActivityNotFoundException) {
            Log.e(TAG, "Description page URL share intent launch failed. (Activity not found)")
        }
    }

    private companion object {
        private const val TAG = "MainActivity"
    }
}