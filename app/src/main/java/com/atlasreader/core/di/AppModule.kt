package com.atlasreader.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.atlasreader.core.common.DefaultDispatcherProvider
import com.atlasreader.core.common.DispatcherProvider
import com.atlasreader.core.common.SystemTimeProvider
import com.atlasreader.core.common.TimeProvider
import com.atlasreader.core.database.AtlasDatabase
import com.atlasreader.core.database.dao.AnnotationDao
import com.atlasreader.core.database.dao.CollectionDao
import com.atlasreader.core.database.dao.CoverDao
import com.atlasreader.core.database.dao.DocumentDao
import com.atlasreader.core.database.dao.ProgressDao
import com.atlasreader.core.database.dao.SearchDao
import com.atlasreader.core.database.dao.TagDao
import com.atlasreader.core.database.dao.UserPreferencesDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindDispatcherProvider(impl: DefaultDispatcherProvider): DispatcherProvider

    @Binds
    @Singleton
    abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AtlasDatabase =
            Room.databaseBuilder(context, AtlasDatabase::class.java, AtlasDatabase.NAME)
                .addCallback(AtlasDatabase.CALLBACK)
                .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
                .build()

        @Provides
        fun provideDocumentDao(db: AtlasDatabase): DocumentDao = db.documentDao()

        @Provides
        fun provideCollectionDao(db: AtlasDatabase): CollectionDao = db.collectionDao()

        @Provides
        fun provideTagDao(db: AtlasDatabase): TagDao = db.tagDao()

        @Provides
        fun provideCoverDao(db: AtlasDatabase): CoverDao = db.coverDao()

        @Provides
        fun provideProgressDao(db: AtlasDatabase): ProgressDao = db.progressDao()

        @Provides
        fun provideAnnotationDao(db: AtlasDatabase): AnnotationDao = db.annotationDao()

        @Provides
        fun provideSearchDao(db: AtlasDatabase): SearchDao = db.searchDao()

        @Provides
        fun provideUserPreferencesDao(db: AtlasDatabase): UserPreferencesDao = db.userPreferencesDao()
    }
}
