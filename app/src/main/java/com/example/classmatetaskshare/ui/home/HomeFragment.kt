package com.example.classmatetaskshare.ui.home

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.classmatetaskshare.*
import com.example.classmatetaskshare.databinding.FragmentHomeBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore // Updated to Firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val dbLocal by lazy {
        Room.databaseBuilder(requireContext().applicationContext, AppDatabase::class.java, "task_db").build()
    }

    // Use Firestore for Cloud Sync
    private val firestore = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        binding.btnSave.setOnClickListener {
            // Automatically get the logged-in user's email
            val sender = FirebaseAuth.getInstance().currentUser?.email ?: "Anonymous"
            val subject = binding.etSubject.text.toString()
            val title = binding.etTitle.text.toString()
            val date = binding.etDate.text.toString()

            if (sender.isEmpty() || subject.isEmpty() || title.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show loading
            binding.progressBar.visibility = View.VISIBLE

            val task = hashMapOf(
                "sender" to sender,
                "subject" to subject,
                "title" to title,
                "deadline" to date
            )

            // Save to Firestore
            firestore.collection("assignments")
                .add(task)
                .addOnSuccessListener {
                    binding.progressBar.visibility = View.GONE // Hide loading on success
                    Toast.makeText(requireContext(), "Shared to Cloud!", Toast.LENGTH_SHORT).show()
                    binding.etTitle.text.clear()
                }
                .addOnFailureListener { e ->
                    binding.progressBar.visibility = View.GONE // Hide loading on failure
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }

            // Local Room Save
            // ... inside binding.btnSave.setOnClickListener ...

// Local Room Save - Include the sender here!
            val localTask = Assignment(
                subject = subject,
                title = title,
                deadline = date,
                sender = sender // Add this line
            )

            lifecycleScope.launch(Dispatchers.IO) {
                dbLocal.assignmentDao().insert(localTask)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}