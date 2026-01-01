package com.example.classmatetaskshare

import androidx.room.*

@Dao
interface AssignmentDao {
    @Insert
    suspend fun insert(assignment: Assignment)

    @Query("SELECT * FROM assignments ORDER BY id DESC")
    suspend fun getAllAssignments(): List<Assignment>
}