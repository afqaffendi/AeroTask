package com.example.classmatetaskshare

import androidx.room.Database
import androidx.room.RoomDatabase


@Database(entities = [Assignment::class], version = 3)
abstract class AppDatabase : RoomDatabase() {
    abstract fun assignmentDao(): AssignmentDao
}