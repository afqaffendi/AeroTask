package com.example.classmatetaskshare

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface AssignmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assignment: Assignment)

    @Update
    suspend fun update(assignment: Assignment)

    @Query("SELECT * FROM assignments ORDER BY timestamp DESC")
    fun getAllTasks(): LiveData<List<Assignment>>

    // STORAGE OPTIMIZATION: Deletes everything except the 20 newest tasks
    @Query("DELETE FROM assignments WHERE id NOT IN (SELECT id FROM assignments ORDER BY timestamp DESC LIMIT 20)")
    suspend fun clearOldCache()

    @Delete
    suspend fun delete(assignment: Assignment)
}