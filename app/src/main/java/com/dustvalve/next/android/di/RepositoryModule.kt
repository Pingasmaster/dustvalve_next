package com.dustvalve.next.android.di

import com.dustvalve.next.android.data.repository.AccountRepositoryImpl
import com.dustvalve.next.android.data.repository.AlbumRepositoryImpl
import com.dustvalve.next.android.data.repository.ArtistRepositoryImpl
import com.dustvalve.next.android.data.repository.CacheRepositoryImpl
import com.dustvalve.next.android.data.repository.DiscoverRepositoryImpl
import com.dustvalve.next.android.data.repository.DownloadRepositoryImpl
import com.dustvalve.next.android.data.repository.LibraryRepositoryImpl
import com.dustvalve.next.android.data.repository.PlaylistRepositoryImpl
import com.dustvalve.next.android.data.repository.SearchRepositoryImpl
import com.dustvalve.next.android.domain.repository.AccountRepository
import com.dustvalve.next.android.domain.repository.AlbumRepository
import com.dustvalve.next.android.domain.repository.ArtistRepository
import com.dustvalve.next.android.domain.repository.CacheRepository
import com.dustvalve.next.android.domain.repository.DiscoverRepository
import com.dustvalve.next.android.domain.repository.DownloadRepository
import com.dustvalve.next.android.domain.repository.LibraryRepository
import com.dustvalve.next.android.domain.repository.PlaylistRepository
import com.dustvalve.next.android.domain.repository.SearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    abstract fun bindArtistRepository(impl: ArtistRepositoryImpl): ArtistRepository

    @Binds
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    abstract fun bindDiscoverRepository(impl: DiscoverRepositoryImpl): DiscoverRepository

    @Binds
    abstract fun bindCacheRepository(impl: CacheRepositoryImpl): CacheRepository

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository
}
