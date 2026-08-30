package com.atlasreader.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.atlasreader.core.database.entity.UserPreferenceEntity

/**
 * Key/value preferences table. Used for library UI state (view mode, sort) and
 * future stats aggregates; participates in backups.
 */
@Dao
interface UserPreferencesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: UserPreferenceEntity)

    @Query("SELECT value FROM user_preferences WHERE key = :key LIMIT 1")
    suspend fun get(key: String): String?

    @Query("DELETE FROM user_preferences WHERE key = :key")
    suspend fun remove(key: String)
}
