package com.dustvalve.next.android.ui.components.sheet

import com.dustvalve.next.android.domain.model.SearchResultType

enum class RemoteItemKind { TRACK, ALBUM, PLAYLIST, ARTIST }

fun SearchResultType.toRemoteKind(): RemoteItemKind? = when (this) {
    SearchResultType.TRACK,
    SearchResultType.YOUTUBE_TRACK,
    SearchResultType.SOUNDCLOUD_TRACK,
    -> RemoteItemKind.TRACK

    SearchResultType.ALBUM,
    SearchResultType.YOUTUBE_ALBUM,
    SearchResultType.SOUNDCLOUD_ALBUM,
    -> RemoteItemKind.ALBUM

    SearchResultType.YOUTUBE_PLAYLIST,
    SearchResultType.SOUNDCLOUD_PLAYLIST,
    -> RemoteItemKind.PLAYLIST

    SearchResultType.ARTIST,
    SearchResultType.YOUTUBE_ARTIST,
    SearchResultType.SOUNDCLOUD_ARTIST,
    -> RemoteItemKind.ARTIST

    SearchResultType.LOCAL_TRACK -> null
}
