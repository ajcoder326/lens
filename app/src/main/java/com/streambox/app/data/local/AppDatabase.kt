package com.streambox.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import com.streambox.app.extension.model.Extension
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM extensions ORDER BY installedAt DESC")
    fun getAllExtensions(): Flow<List<Extension>>
    
    @Query("SELECT * FROM extensions WHERE enabled = 1 ORDER BY installedAt DESC")
    fun getEnabledExtensions(): Flow<List<Extension>>
    
    @Query("SELECT * FROM extensions WHERE id = :id")
    suspend fun getById(id: String): Extension?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(extension: Extension)
    
    @Update
    suspend fun update(extension: Extension)
    
    @Query("DELETE FROM extensions WHERE id = :id")
    suspend fun deleteById(id: String)
    
    @Delete
    suspend fun delete(extension: Extension)
    
    @Query("UPDATE extensions SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}

@Database(
    entities = [Extension::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun extensionDao(): ExtensionDao
}
