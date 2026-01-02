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
import kotlinx.coroutines.withContext
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

        checkNotificationPermission()

        binding.etDeadline.setOnClickListener {
            showDateTimePicker()
        }

        binding.btnSave.setOnClickListener {
            animateView(it) { saveTaskToCloud() }
        }

        binding.btnShare.setOnClickListener {
            animateView(it) { shareTaskExternally() }
        }
    }

    private fun animateView(view: View, onEnd: () -> Unit) {
        view.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .withEndAction { onEnd() }
                    .start()
            }.start()
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

        // 1. Get current user info
        val currentUser = auth.currentUser
        val currentUserId = currentUser?.uid
        val currentUserEmail = currentUser?.email

        if (currentUserId == null) {
            Toast.makeText(requireContext(), "Error: User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        if (subject.isEmpty() || title.isEmpty() || date.isEmpty()) {
            Toast.makeText(requireContext(), "Fill in Subject, Title, and Date", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Fetch YOUR account-specific class code from SharedPreferences
        val prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val userSpecificKey = "class_code_$currentUserId" // Unique to this account
        val myCurrentClassCode = prefs.getString(userSpecificKey, "PRIVATE") ?: "PRIVATE"

        setLoadingState(true)

        // 3. Create the Task Map with the correct Class Code
        val taskMap = hashMapOf(
            "sender" to if (nickname.isEmpty()) "Anonymous" else nickname,
            "senderEmail" to currentUserEmail,
            "subject" to subject,
            "title" to title,
            "deadline" to date,
            "isDone" to false,
            "classCode" to myCurrentClassCode, // STAMPED with your specific group code
            "userId" to currentUserId         // STAMPED with your account ID
        )

        firestore.collection("assignments").add(taskMap)
            .addOnSuccessListener { documentReference ->
                scheduleNotification(title, subject, date)

                val localTask = Assignment(
                    docId = documentReference.id,
                    classCode = myCurrentClassCode,
                    subject = subject,
                    title = title,
                    deadline = date,
                    sender = if (nickname.isEmpty()) "Anonymous" else nickname,
                    senderEmail = currentUserEmail ?: ""
                )

                lifecycleScope.launch(Dispatchers.IO) {
                    dbLocal.assignmentDao().insert(localTask)
                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        val msg = if (myCurrentClassCode == "PRIVATE") "Saved Privately" else "Shared with Group!"
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                        findNavController().navigate(R.id.nav_gallery)
                    }
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
            val delay = (deadlineDate!!.time - currentTime) - (60 * 60 * 1000)

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
        } catch (e: Exception) { e.printStackTrace() }
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