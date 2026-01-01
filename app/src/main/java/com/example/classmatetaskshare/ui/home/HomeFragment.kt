package com.example.classmatetaskshare.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.example.classmatetaskshare.*
import com.example.classmatetaskshare.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // Requirement: Offline Functionality
    private val db by lazy {
        Room.databaseBuilder(requireContext().applicationContext, AppDatabase::class.java, "task_db").build()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHomeBinding.bind(view)

        binding.btnSave.setOnClickListener {
            val task = Assignment(
                subject = binding.etSubject.text.toString(),
                title = binding.etTitle.text.toString(),
                deadline = binding.etDate.text.toString()
            )
            // Save to Database in background thread
            lifecycleScope.launch(Dispatchers.IO) {
                db.assignmentDao().insert(task)
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Saved Offline!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Requirement: Collaboration (Sharing via Intent)
        binding.btnShare.setOnClickListener {
            val shareText = "Assignment Reminder!\nSubject: ${binding.etSubject.text}\nTask: ${binding.etTitle.text}\nDue: ${binding.etDate.text}"
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            startActivity(Intent.createChooser(intent, "Collaborate via..."))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}