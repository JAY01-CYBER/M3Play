package com.jay.m3play.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.jay.m3play.LocalPlayerAwareWindowInsets
import com.jay.m3play.LocalPlayerConnection
import com.jay.m3play.R
import com.jay.m3play.constants.CONTENT_TYPE_HEADER
import com.jay.m3play.constants.CONTENT_TYPE_SONG
import com.jay.m3play.constants.SongFilter
import com.jay.m3play.constants.SongFilterKey
import com.jay.m3play.constants.SongSortDescendingKey
import com.jay.m3play.constants.SongSortType
import com.jay.m3play.constants.SongSortTypeKey
import com.jay.m3play.constants.YtmSyncKey
import com.jay.m3play.extensions.toMediaItem
import com.jay.m3play.extensions.togglePlayPause
import com.jay.m3play.playback.queues.ListQueue
import com.jay.m3play.ui.component.ChipsRow
import com.jay.m3play.ui.component.HideOnScrollFAB
import com.jay.m3play.ui.component.LocalMenuState
import com.jay.m3play.ui.component.SongListItem
import com.jay.m3play.ui.component.SortHeader
import com.jay.m3play.ui.component.VerticalFastScroller
import com.jay.m3play.ui.menu.SelectionSongMenu
import com.jay.m3play.ui.menu.SongMenu
import com.jay.m3play.ui.utils.ItemWrapper
import com.jay.m3play.utils.rememberEnumPreference
import com.jay.m3play.utils.rememberPreference
import com.jay.m3play.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val songs by viewModel.allSongs.collectAsState()
    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            when (filter) {
                SongFilter.LIKED -> viewModel.syncLikedSongs()
                SongFilter.LIBRARY -> viewModel.syncLibrarySongs()
                else -> return@LaunchedEffect
            }
        }
    }

    val wrappedSongs = songs.map { item -> ItemWrapper(item) }.toMutableList()
    var selection by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        VerticalFastScroller(listState = lazyListState, topContentPadding = 16.dp, endContentPadding = 0.dp) {
            LazyColumn(state = lazyListState, contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
                item(key = "filter", contentType = CONTENT_TYPE_HEADER) {
                    Row {
                        Spacer(Modifier.width(12.dp))
                        FilterChip(
                            label = { Text(stringResource(R.string.songs)) },
                            selected = true,
                            onClick = onDeselect,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = { Icon(painterResource(R.drawable.close), null) }
                        )
                        ChipsRow(
                            chips = listOf(SongFilter.LIKED to stringResource(R.string.filter_liked), SongFilter.LIBRARY to stringResource(R.string.filter_library)),
                            currentValue = filter,
                            onValueUpdate = { filter = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                itemsIndexed(items = wrappedSongs, key = { _, item -> item.item.id }, contentType = { _, _ -> CONTENT_TYPE_SONG }) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item,
                        isActive = songWrapper.item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        modifier = Modifier.fillMaxWidth().animateItem()
                    )
                }
            }
        }
        HideOnScrollFAB(songs.isNotEmpty(), lazyListState, R.drawable.shuffle, onClick = {})
    }
}
