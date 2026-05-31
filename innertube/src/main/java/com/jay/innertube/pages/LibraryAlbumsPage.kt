package com.jay.innertube.pages

import com.jay.innertube.models.Album
import com.jay.innertube.models.AlbumItem
import com.jay.innertube.models.Artist
import com.jay.innertube.models.ArtistItem
import com.jay.innertube.models.MusicResponsiveListItemRenderer
import com.jay.innertube.models.MusicTwoRowItemRenderer
import com.jay.innertube.models.PlaylistItem
import com.jay.innertube.models.SongItem
import com.jay.innertube.models.YTItem
import com.jay.innertube.models.oddElements
import com.jay.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.musicThumbnailRenderer?.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
