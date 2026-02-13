package com.example.classmatetaskshare.ui.home

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(requireContext(), "Notifications disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    // UPDATED: Now uses the Singleton AppDatabase to prevent ANRs and Binder Traffic crashes
    private val dbLocal by lazy { AppDatabase.getDatabase(requireContext()) }

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        playCascadeAnimation()
        checkNotificationPermission()
        binding.etDeadline.setOnClickListener { showDateTimePicker() }
        binding.btnSave.setOnClickListener { animateView(it) { saveTaskLocallyFirst() } }
    }

    private fun playCascadeAnimation() {
        val views = listOf(
            binding.tvGreeting, binding.layoutTitle, binding.etSubject,
            binding.etDeadline, binding.etSenderName, binding.btnSave
        )
        views.forEach { it.alpha = 0f; it.scaleX = 0.7f; it.scaleY = 0.7f }
        views.forEachIndexed { i, v ->
            v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(100L * i).setDuration(500)
                .setInterpolator(OvershootInterpolator(1.2f)).start()
        }
    }

    private fun showSuccessCelebration() {
        // IMPORTANT: Ensure ic_cloud_done exists in drawables or this line will crash the app
        binding.successOverlay.visibility = View.VISIBLE
        binding.successOverlay.alpha = 0f
        binding.successOverlay.animate().alpha(1f).setDuration(300).start()

        binding.ivConfetti.scaleX = 0f
        binding.ivConfetti.scaleY = 0f
        binding.ivConfetti.animate()
            .scaleX(1.4f).scaleY(1.4f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator(2f))
            .withEndAction {
                lifecycleScope.launch {
                    delay(1200)
                    findNavController().navigate(R.id.nav_gallery)
                }
            }.start()
    }

    private fun saveTaskLocallyFirst() {
        val nickname = binding.etSenderName.text.toString().trim()
        val subject = binding.etSubject.text.toString().trim()
        val title = binding.etTitle.text.toString().trim()
        val date = binding.etDeadline.text.toString().trim()

        val currentUser = auth.currentUser ?: return
        val currentUserId = currentUser.uid

        if (subject.isEmpty() || title.isEmpty() || date.isEmpty()) {
            Toast.makeText(requireContext(), "Fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        val prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val myClassCode = prefs.getString("class_code_$currentUserId", "PRIVATE") ?: "PRIVATE"

        val localTask = Assignment(
            docId = "TEMP_${System.currentTimeMillis()}",
            classCode = myClassCode,
            subject = subject,
            title = title,
            deadline = date,
            sender = nickname.ifEmpty { "Anonymous" },
            senderEmail = currentUser.email ?: "",
            timestamp = System.currentTimeMillis()
        )

        setLoadingState(true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                dbLocal.assignmentDao().insert(localTask)
                dbLocal.assignmentDao().clearOldCache() // Optimization query

                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    scheduleNotification(title, subject, date)
                    syncToFirestore(localTask, nickname, currentUser.email, myClassCode, currentUserId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    Toast.makeText(requireContext(), "Database Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun syncToFirestore(localTask: Assignment, nickname: String, email: String?, classCode: String, userId: String) {
        val taskMap = hashMapOf(
            "sender" to nickname.ifEmpty { "Anonymous" },
            "senderEmail" to email,
            "subject" to localTask.subject,
            "title" to localTask.title,
            "deadline" to localTask.deadline,
            "isDone" to false,
            "classCode" to classCode,
            "userId" to userId
        )

        firestore.collection("assignments").add(taskMap)
            .addOnSuccessListener { docRef ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val syncedTask = localTask.copy(docId = docRef.id)
                    dbLocal.assignmentDao().update(syncedTask)
                    withContext(Dispatchers.Main) { showSuccessCelebration() }
                }
            }
            .addOnFailureListener {
                findNavController().navigate(R.id.nav_gallery)
            }
    }

    private fun showDateTimePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(requireContext(), { _, year, month, day ->
            TimePickerDialog(requireContext(), { _, hour, minute ->
                val formattedDateTime = String.format(Locale.getDefault(), "%02d/%02d/%04d at %02d:%02d", day, month + 1, year, hour, minute)
                binding.etDeadline.setText(formattedDateTime)
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun scheduleNotification(title: String, subject: String, deadlineStr: String) {
        val sdf = SimpleDateFormat("dd/MM/yyyy 'at' HH:mm", Locale.getDefault())
        try {
            val deadlineDate = sdf.parse(deadlineStr)
            val delay = (deadlineDate?.time ?: 0) - System.currentTimeMillis() - (60 * 60 * 1000)

            if (delay > 0) {
                val data = workDataOf("title" to "Deadline: $title", "subject" to "$subject is due soon!")
                val request = OneTimeWorkRequestBuilder<NotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()
                WorkManager.getInstance(requireContext()).enqueue(request)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setLoadingState(isLoading: Boolean) {
        _binding?.let {
            it.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            it.btnSave.isEnabled = !isLoading
        }
    }

    private fun animateView(view: View, onEnd: () -> Unit) {
        view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).withEndAction { onEnd() }.start()
        }.start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}