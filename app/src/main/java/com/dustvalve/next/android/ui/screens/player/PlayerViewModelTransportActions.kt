package com.dustvalve.next.android.ui.screens.player

import android.media.AudioDeviceInfo
import com.dustvalve.next.android.domain.model.Track

/** Transport / play / seek / volume actions for [PlayerViewModel]. */
fun PlayerViewModel.setAudioOutputDevice(device: AudioDeviceInfo?) = audio.setAudioOutputDevice(device)

fun PlayerViewModel.setVolume(level: Float) = audio.setVolume(level)

fun PlayerViewModel.onPlayPause() = library.onPlayPause()

fun PlayerViewModel.onNext() = library.onNext()

fun PlayerViewModel.onPrevious() = library.onPrevious()

fun PlayerViewModel.onSeek(ms: Long) = library.onSeek(ms)

fun PlayerViewModel.onStop() = library.onStop()

fun PlayerViewModel.onToggleShuffle() = library.onToggleShuffle()

fun PlayerViewModel.onToggleRepeat() = library.onToggleRepeat()

fun PlayerViewModel.playTrack(track: Track) = play.playTrack(track)

suspend fun PlayerViewModel.playTrackAwaiting(track: Track): Boolean = play.playTrackAwaiting(track)

fun PlayerViewModel.playTrackInList(tracks: List<Track>, index: Int) = play.playTrackInList(tracks, index)

suspend fun PlayerViewModel.playTrackInListAwaiting(tracks: List<Track>, index: Int): Boolean =
    play.playTrackInListAwaiting(tracks, index)

fun PlayerViewModel.playAlbum(tracks: List<Track>, startIndex: Int) = play.playAlbum(tracks, startIndex)

suspend fun PlayerViewModel.playAlbumAwaiting(tracks: List<Track>, startIndex: Int): Boolean =
    play.playAlbumAwaiting(tracks, startIndex)

fun PlayerViewModel.skipToQueueIndex(index: Int) = play.skipToQueueIndex(index)

fun PlayerViewModel.playQueueEntry(uid: Long) = library.playQueueEntry(uid, play::skipToQueueIndex)

fun PlayerViewModel.removeQueueEntry(uid: Long) = library.removeQueueEntry(uid)

fun PlayerViewModel.moveQueueEntry(fromUid: Long, toUid: Long) = library.moveQueueEntry(fromUid, toUid)
