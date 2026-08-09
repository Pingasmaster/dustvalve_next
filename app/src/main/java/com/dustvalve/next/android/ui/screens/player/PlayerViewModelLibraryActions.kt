package com.dustvalve.next.android.ui.screens.player

import com.dustvalve.next.android.domain.model.Track

/** Favorites / downloads / playlists / queue-add actions for [PlayerViewModel]. */
fun PlayerViewModel.onToggleFavorite() = library.onToggleFavorite()

fun PlayerViewModel.onDownloadTrack() = library.onDownloadTrack()

fun PlayerViewModel.onDeleteTrackDownload() = library.onDeleteTrackDownload()

fun PlayerViewModel.addToPlaylist(playlistId: String) = library.addToPlaylist(playlistId)

fun PlayerViewModel.createPlaylistAndAddTrack(name: String, shapeKey: String?, iconUrl: String?) =
    library.createPlaylistAndAddTrack(name, shapeKey, iconUrl)

fun PlayerViewModel.toggleFavoriteById(trackId: String) = library.toggleFavoriteById(trackId)

fun PlayerViewModel.playNext(track: Track) = library.playNext(track)

fun PlayerViewModel.addToQueue(track: Track) = library.addToQueue(track)

fun PlayerViewModel.addAllToQueue(tracks: List<Track>) = library.addAllToQueue(tracks)

fun PlayerViewModel.addTrackToPlaylist(playlistId: String, trackId: String) =
    library.addTrackToPlaylist(playlistId, trackId)

fun PlayerViewModel.createPlaylistAndAddArbitraryTrack(
    name: String,
    shapeKey: String?,
    iconUrl: String?,
    trackId: String,
) = library.createPlaylistAndAddArbitraryTrack(name, shapeKey, iconUrl, trackId)

fun PlayerViewModel.clearSnackbar() = library.clearSnackbar()

fun PlayerViewModel.showNoAlbumSnackbar() = library.showNoAlbumSnackbar()
