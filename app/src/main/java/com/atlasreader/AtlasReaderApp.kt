package com.atlasreader

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point. Registers the Hilt worker factory so WorkManager
 * workers get dependency injection (the default WorkManager initializer is
 * removed in the manifest).
 */
@HiltAndroidApp
class AtlasReaderApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
