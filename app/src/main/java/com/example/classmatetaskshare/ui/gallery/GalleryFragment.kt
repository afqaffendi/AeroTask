package com.example.classmatetaskshare.ui.gallery

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.example.classmatetaskshare.AppDatabase
import com.example.classmatetaskshare.Assignment
import com.example.classmatetaskshare.R
import com.example.classmatetaskshare.databinding.FragmentGalleryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GalleryFragment : Fragment(R.layout.fragment_gallery) {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AssignmentAdapter

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val dbLocal by lazy {
        Room.databaseBuilder(requireContext().applicationContext, AppDatabase::class.java, "task_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGalleryBinding.bind(view)

        setupRecyclerView()
        setupNavigationListeners()
        setupSearch()
        setupSwipeToDelete()
        fetchCloudData() // This now uses account-specific filtering
    }

    private fun setupRecyclerView() {
        adapter = AssignmentAdapter(
            assignments = emptyList(),
            onFilterResult = { isEmpty ->
                binding.tvEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
            },
            onCheckedChange = { assignment, isChecked ->
                updateLocalProgress(assignment, isChecked)
            },
            onDeleteTask = { task, color ->
                if (task.senderEmail == auth.currentUser?.email) {
                    showDeleteConfirmation(task, color)
                } else {
                    Toast.makeText(requireContext(), "Only owners can delete tasks", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerViewGallery.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewGallery.adapter = adapter
    }

    private fun setupNavigationListeners() {
        binding.btnCreateTask.setOnClickListener {
            animateView(it) { findNavController().navigate(R.id.nav_home) }
        }

        binding.btnNavHome.setOnClickListener {
            animateView(it) { binding.recyclerViewGallery.smoothScrollToPosition(0) }
        }

        binding.btnNavChat.setOnClickListener {
            animateView(it) { findNavController().navigate(R.id.nav_slideshow) }
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = adapter.getAssignmentAt(position)

                if (task.senderEmail == auth.currentUser?.email) {
                    showDeleteConfirmation(task, Color.LTGRAY, position)
                } else {
                    adapter.notifyItemChanged(position)
                    Toast.makeText(requireContext(), "Only owners can delete tasks", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerViewGallery)
    }

    private fun showDeleteConfirmation(task: Assignment, accentColor: Int, position: Int? = null) {
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.RoundedDialog)
            .setIcon(R.drawable.ic_menu_delete)
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to remove '${task.title}'? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("assignments").document(task.docId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Task deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "Error deleting task", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel") { d, _ ->
                position?.let { adapter.notifyItemChanged(it) }
                d.dismiss()
            }
            .show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(Color.RED)
        dialog.getButton(android.content.DialogInterface.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
    }

    private fun animateView(view: View, onEnd: () -> Unit) {
        view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).withEndAction {
            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).withEndAction { onEnd() }.start()
        }.start()
    }

    private fun updateLocalProgress(task: Assignment, isDone: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedTask = task.copy(isDone = isDone)
            dbLocal.assignmentDao().update(updatedTask)
        }
    }

    /**
     * ACCOUNT-SPECIFIC DATA FETCHING:
     * This pulls the class code specifically saved to the logged-in user's UID.
     */
    private fun fetchCloudData() {
        val currentUserId = auth.currentUser?.uid ?: return

        // 1. Access AppPrefs (matching the HomeFragment we just updated)
        val prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // 2. Get the specific key for THIS user
        val userSpecificKey = "class_code_$currentUserId"
        val myClassCode = prefs.getString(userSpecificKey, "PRIVATE") ?: "PRIVATE"

        // 3. Query Firestore for tasks belonging to THIS specific class folder
        firestore.collection("assignments")
            .whereEqualTo("classCode", myClassCode)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    return@addSnapshotListener
                }

                val cloudList = snapshots?.map { doc ->
                    Assignment(
                        docId = doc.id,
                        subject = doc.getString("subject") ?: "",
                        title = doc.getString("title") ?: "",
                        deadline = doc.getString("deadline") ?: "",
                        sender = doc.getString("sender") ?: "",
                        senderEmail = doc.getString("senderEmail") ?: "",
                        classCode = doc.getString("classCode") ?: "",
                        isDone = doc.getBoolean("isDone") ?: false
                    )
                } ?: emptyList()

                adapter.updateData(cloudList)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}