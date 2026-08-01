package com.dustvalve.next.android.ui.components.sheet

import com.dustvalve.next.android.domain.model.SearchResultType

enum class RemoteItemKind { TRACK, ALBUM, PLAYLIST, ARTIST }

fun SearchResultType.toRemoteKind(): RemoteItemKind? = when (this) {
    SearchResultType.TRACK,
    SearchResultType.YOUTUBE_TRACK,
    -> RemoteItemKind.TRACK

    SearchResultType.ALBUM,
    SearchResultType.YOUTUBE_ALBUM,
    -> RemoteItemKind.ALBUM

    SearchResultType.YOUTUBE_PLAYLIST -> RemoteItemKind.PLAYLIST

    SearchResultType.ARTIST,
    SearchResultType.YOUTUBE_ARTIST,
    -> RemoteItemKind.ARTIST

    SearchResultType.LOCAL_TRACK -> null
}
