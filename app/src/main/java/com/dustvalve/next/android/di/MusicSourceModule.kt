package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.repository.BandcampMusicSource
import com.dustvalve.next.android.data.repository.YouTubeMusicSource
import com.dustvalve.next.android.data.repository.YouTubeSource
import com.dustvalve.next.android.domain.repository.MusicSource
import com.dustvalve.next.android.domain.repository.MusicSourceRegistry
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MusicSourceModule {

    @Provides
    @Singleton
    fun provideMusicSourceRegistry(
        bandcamp: BandcampMusicSource,
        youtube: YouTubeSource,
        youtubeMusic: YouTubeMusicSource,
    ): MusicSourceRegistry {
        val sources = listOf(bandcamp, youtube, youtubeMusic)
        val byId = sources.associateBy { it.id }
        return object : MusicSourceRegistry {
            override fun all(): List<MusicSource> = sources
            override fun get(id: String): MusicSource? = byId[id]
        }
    }
}
