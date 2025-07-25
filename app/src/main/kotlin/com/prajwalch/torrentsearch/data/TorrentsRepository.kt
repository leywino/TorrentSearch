package com.prajwalch.torrentsearch.data

import com.prajwalch.torrentsearch.models.Category
import com.prajwalch.torrentsearch.models.Torrent
import com.prajwalch.torrentsearch.network.HttpClient
import com.prajwalch.torrentsearch.network.HttpClientResponse

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope

class TorrentsRepository(private val httpClient: HttpClient) {
    /** Starts a search for the given query. */
    suspend fun search(
        query: String,
        category: Category,
        providers: List<SearchProvider>,
    ): TorrentsRepositoryResult {
        val query = query.replace(' ', '+').trim()
        val context = SearchContext(category = category, httpClient = httpClient)

        return supervisorScope {
            val results = chooseSearchProviders(providers = providers, category = category)
                .map { async(Dispatchers.IO) { it.search(query = query, context = context) } }
                .map { httpClient.withExceptionHandler { it.await() } }

            // Check for network error.
            if (results.all { it is HttpClientResponse.Error.NetworkError }) {
                TorrentsRepositoryResult(isNetworkError = true)
            }

            val torrents = results
                .mapNotNull { it as? HttpClientResponse.Ok }
                .flatMap { it.result }
                .sortedByDescending { it.seeds }

            TorrentsRepositoryResult(torrents = torrents)
        }
    }

    /**
     * Returns the search providers that is capable of handling the given
     * category.
     */
    private fun chooseSearchProviders(
        providers: List<SearchProvider>,
        category: Category,
    ): List<SearchProvider> {
        if (category == Category.All) {
            return providers
        }

        return providers.filter {
            (it.specializedCategory == Category.All) || (category == it.specializedCategory)
        }
    }

    /** Returns `true` is the internet is available to perform a `search()`. */
    suspend fun isInternetAvailable(): Boolean = coroutineScope { httpClient.isInternetAvailable() }
}

data class TorrentsRepositoryResult(
    val torrents: List<Torrent>? = null,
    /** Indicates whether network error happened or not. */
    val isNetworkError: Boolean = false,
)