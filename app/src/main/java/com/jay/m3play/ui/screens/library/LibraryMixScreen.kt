package com.jay.m3play.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.jay.m3play.LocalPlayerAwareWindowInsets
import com.jay.m3play.LocalPlayerConnection
import com.jay.m3play.R
import com.jay.m3play.constants.AlbumViewTypeKey
import com.jay.m3play.constants.CONTENT_TYPE_HEADER
import com.jay.m3play.constants.CONTENT_TYPE_PLAYLIST
import com.jay.m3play.constants.GridItemSize
import com.jay.m3play.constants.GridItemsSizeKey
import com.jay.m3play.constants.GridThumbnailHeight
import com.jay.m3play.constants.LibraryViewType
import com.jay.m3play.constants.MixSortDescendingKey
import com.jay.m3play.constants.MixSortType
import com.jay.m3play.constants.MixSortTypeKey
import com.jay.m3play.db.entities.Album
import com.jay.m3play.db.entities.Artist
import com.jay.m3play.db.entities.Playlist
import com.jay.m3play.extensions.reversed
import com.jay.m3play.ui.component.AlbumGridItem
import com.jay.m3play.ui.component.AlbumListItem
import com.jay.m3play.ui.component.ArtistGridItem
import com.jay.m3play.ui.component.ArtistListItem
import com.jay.m3play.ui.component.LocalMenuState
import com.jay.m3play.ui.component.PlaylistGridItem
import com.jay.m3play.ui.component.PlaylistListItem
import com.jay.m3play.ui.component.SortHeader
import com.jay.m3play.ui.menu.AlbumMenu
import com.jay.m3play.ui.menu.ArtistMenu
import com.jay.m3play.ui.menu.PlaylistMenu
import com.jay.m3play.utils.rememberEnumPreference
import com.jay.m3play.utils.rememberPreference
import com.jay.m3play.viewmodels.LibraryMixViewModel
import java.text.Collator
import java.time.LocalDateTime
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: @Composable () -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val (sortType, onSortTypeChange) = rememberEnumPreference(
        MixSortTypeKey,
        MixSortType.CREATE_DATE
    )
    val (sortDescending, onSortDescendingChange) = rememberPreference(MixSortDescendingKey, true)
    val gridItemSize by rememberEnumPreference(GridItemsSizeKey, GridItemSize.BIG)

    val albums by viewModel.albums.collectAsState()
    val artist by viewModel.artists.collectAsState()
    val playlist by viewModel.playlists.collectAsState()

    var allItems = albums + artist + playlist
    val collator = Collator.getInstance(Locale.getDefault())
    collator.strength = Collator.PRIMARY
    allItems =
        when (sortType) {
            MixSortType.CREATE_DATE ->
                allItems.sortedBy { item ->
                    when (item) {
                        is Album -> item.album.bookmarkedAt
                        is Artist -> item.artist.bookmarkedAt
                        is Playlist -> item.playlist.createdAt
                        else -> LocalDateTime.now()
                    }
                }
            MixSortType.NAME ->
                allItems.sortedWith(
                    compareBy(collator) { item ->
                        when (item) {
                            is Album -> item.album.title
                            is Artist -> item.artist.name
                            is Playlist -> item.playlist.name
                            else -> ""
                        }
                    },
                )
            MixSortType.LAST_UPDATED ->
                allItems.sortedBy { item ->
                    when (item) {
                        is Album -> item.album.lastUpdateTime
                        is Artist -> item.artist.lastUpdateTime
                        is Playlist -> item.playlist.lastUpdateTime
                        else -> LocalDateTime.now()
                    }
                }
        }.reversed(sortDescending)

    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    val headerContent = @Composable {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp),
        ) {
            SortHeader(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = onSortTypeChange,
                onSortDescendingChange = onSortDescendingChange,
                sortTypeText = { sortType ->
                    when (sortType) {
                        MixSortType.CREATE_DATE -> R.string.sort_by_create_date
                        MixSortType.LAST_UPDATED -> R.string.sort_by_last_updated
                        MixSortType.NAME -> R.string.sort_by_name
                    }
                },
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewType = viewType.toggle() },
                modifier = Modifier.padding(start = 6.dp, end = 6.dp),
            ) {
                Icon(
                    painter = painterResource(if (viewType == LibraryViewType.LIST) R.drawable.list else R.drawable.grid_view),
                    contentDescription = null,
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (viewType == LibraryViewType.LIST) {
            LazyColumn(
                state = lazyListState,
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item(key = "filter", contentType = CONTENT_TYPE_HEADER) { filterContent() }
                item(key = "header", contentType = CONTENT_TYPE_HEADER) { headerContent() }
                items(items = allItems, key = { it.id }, contentType = { CONTENT_TYPE_PLAYLIST }) { item ->
                    
                    when (item) {
                        is Playlist -> PlaylistListItem(playlist = item, modifier = Modifier.animateItem())
                        is Artist -> ArtistListItem(artist = item, modifier = Modifier.animateItem())
                        is Album -> AlbumListItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, modifier = Modifier.animateItem())
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + if (gridItemSize == GridItemSize.BIG) 24.dp else (-24).dp),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                item(key = "filter", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) { filterContent() }
                item(key = "header", span = { GridItemSpan(maxLineSpan) }, contentType = CONTENT_TYPE_HEADER) { headerContent() }
                items(items = allItems, key = { it.id }, contentType = { CONTENT_TYPE_PLAYLIST }) { item ->
                    when (item) {
                        is Playlist -> PlaylistGridItem(playlist = item, modifier = Modifier.animateItem())
                        is Artist -> ArtistGridItem(artist = item, modifier = Modifier.animateItem())
                        is Album -> AlbumGridItem(album = item, isActive = item.id == mediaMetadata?.album?.id, isPlaying = isPlaying, modifier = Modifier.animateItem())
                    }
                }
            }
        }
    }
}
