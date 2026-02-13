package com.example.classmatetaskshare

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "assignments")
data class Assignment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val docId: String = "",
    val classCode: String = "",
    val subject: String = "",
    val title: String = "",
    val deadline: String = "",
    val sender: String = "",
    val senderEmail: String = "",
    val isDone: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)