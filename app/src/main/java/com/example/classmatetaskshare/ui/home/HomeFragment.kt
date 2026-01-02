package com.example.classmatetaskshare.ui.home

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.classmatetaskshare.*
import com.example.classmatetaskshare.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val dbLocal by lazy {
        Room.databaseBuilder(requireContext().applicationContext, AppDatabase::class.java, "task_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        // 1. Request Notification Permissions for Android 13+
        checkNotificationPermission()

        binding.etDeadline.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnSave.setOnClickListener {
            saveTaskToCloud()
        }

        binding.btnShare.setOnClickListener {
            shareTaskExternally()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val formattedDateTime = String.format("%02d/%02d/%d at %02d:%02d", day, month + 1, year, hour, minute)
                binding.etDeadline.setText(formattedDateTime)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveTaskToCloud() {
        val nickname = binding.etSenderName.text.toString().trim()
        val subject = binding.etSubject.text.toString().trim()
        val title = binding.etTitle.text.toString().trim()
        val date = binding.etDeadline.text.toString().trim()

        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val classCode = sharedPref.getString("class_code", "GLOBAL") ?: "GLOBAL"
        val currentUserEmail = auth.currentUser?.email ?: "Anonymous"

        if (subject.isEmpty() || title.isEmpty() || date.isEmpty()) {
            Toast.makeText(requireContext(), "Fill in Subject, Title, and Date", Toast.LENGTH_SHORT).show()
            return
        }

        setLoadingState(true)

        val taskMap = hashMapOf(
            "classCode" to classCode,
            "sender" to if (nickname.isEmpty()) "Anonymous" else nickname,
            "senderEmail" to currentUserEmail,
            "subject" to subject,
            "title" to title,
            "deadline" to date
        )

        firestore.collection("assignments").add(taskMap)
            .addOnSuccessListener { documentReference ->
                // 2. Schedule Local Notification
                scheduleNotification(title, subject, date)

                val localTask = Assignment(
                    docId = documentReference.id,
                    classCode = classCode,
                    subject = subject,
                    title = title,
                    deadline = date,
                    sender = if (nickname.isEmpty()) "Anonymous" else nickname,
                    senderEmail = currentUserEmail
                )

                lifecycleScope.launch(Dispatchers.IO) {
                    dbLocal.assignmentDao().insert(localTask)
                }

                _binding?.let {
                    Toast.makeText(requireContext(), "Shared with Class $classCode!", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                }
            }
            .addOnFailureListener { e ->
                setLoadingState(false)
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun scheduleNotification(title: String, subject: String, deadlineStr: String) {
        val sdf = SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault())
        try {
            val deadlineDate = sdf.parse(deadlineStr)
            val currentTime = System.currentTimeMillis()

            // Calculate delay: Trigger 1 hour (3600000ms) before the deadline
            // val delay = (deadlineDate!!.time - currentTime) - (60 * 60 * 1000)
            val delay = 5000L
            if (delay > 0) {
                val data = workDataOf(
                    "title" to "Deadline Reminder: $title",
                    "subject" to "$subject is due in 1 hour!"
                )

                val notificationRequest = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()

                WorkManager.getInstance(requireContext()).enqueue(notificationRequest)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun shareTaskExternally() {
        val shareBody = "📚 *New Assignment*\nSubject: ${binding.etSubject.text}\nTask: ${binding.etTitle.text}\nDue: ${binding.etDeadline.text}"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun setLoadingState(isLoading: Boolean) {
        _binding?.let {
            it.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            it.btnSave.isEnabled = !isLoading
            it.btnShare.isEnabled = !isLoading
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}