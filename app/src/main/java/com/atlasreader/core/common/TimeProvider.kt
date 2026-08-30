package com.atlasreader.core.common

import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Clock abstraction so reading-statistics and recents logic is testable.
 */
interface TimeProvider {
    fun now(): Instant
    fun epochMillis(): Long = now().toEpochMilli()
}

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
}
