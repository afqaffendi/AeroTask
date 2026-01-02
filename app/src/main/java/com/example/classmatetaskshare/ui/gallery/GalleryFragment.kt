package com.example.classmatetaskshare.ui.gallery

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GalleryFragment : Fragment(R.layout.fragment_gallery) {
    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: AssignmentAdapter

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // Initialize Room Database
    private val dbLocal by lazy {
        Room.databaseBuilder(requireContext().applicationContext, AppDatabase::class.java, "task_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGalleryBinding.bind(view)

        // Initialize Adapter with 3 parameters: List, Filter Callback, and Checkbox Callback
        adapter = AssignmentAdapter(
            emptyList(),
            { isEmpty -> _binding?.tvEmptyState?.visibility = if (isEmpty) View.VISIBLE else View.GONE },
            { assignment, isChecked ->
                // Update "Mark as Done" status in Room (Local Progress)
                updateLocalProgress(assignment, isChecked)
            }
        )

        binding.recyclerViewGallery.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewGallery.adapter = adapter

        binding.fabAddTask.setOnClickListener {
            findNavController().navigate(R.id.nav_home)
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })

        setupSwipeToDelete()
        fetchCloudData()
    }

    private fun updateLocalProgress(task: Assignment, isDone: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val updatedTask = task.copy(isDone = isDone)
            dbLocal.assignmentDao().update(updatedTask)
        }
    }

    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = adapter.getAssignmentAt(position)
                val userEmail = auth.currentUser?.email ?: ""

                // Only allow the person who created the task to delete it from the cloud
                if (task.senderEmail == userEmail) {
                    showDeleteConfirmation(task, position)
                } else {
                    adapter.notifyItemChanged(position)
                    Toast.makeText(requireContext(), "You can only delete your own tasks!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerViewGallery)
    }

    private fun showDeleteConfirmation(task: Assignment, position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Task")
            .setMessage("Permanently remove '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("assignments").document(task.docId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Deleted from Cloud", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel") { d, _ ->
                adapter.notifyItemChanged(position)
                d.dismiss()
            }
            .show()
    }

    private fun fetchCloudData() {
        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val classCode = sharedPref.getString("class_code", "") ?: ""

        if (classCode.isEmpty()) {
            _binding?.tvEmptyState?.text = "Join a class in Profile to see reminders!"
            _binding?.tvEmptyState?.visibility = View.VISIBLE
            return
        }

        firestore.collection("assignments")
            .whereEqualTo("classCode", classCode)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                // We fetch from Cloud, but we need to check Room for the "isDone" checkmark
                lifecycleScope.launch(Dispatchers.IO) {
                    val cloudList = snapshots?.map { doc ->
                        Assignment(
                            docId = doc.id,
                            subject = doc.getString("subject") ?: "",
                            title = doc.getString("title") ?: "",
                            deadline = doc.getString("deadline") ?: "",
                            sender = doc.getString("sender") ?: "",
                            senderEmail = doc.getString("senderEmail") ?: "",
                            classCode = doc.getString("classCode") ?: ""
                        )
                    } ?: emptyList()

                    // Match cloud items with local "Done" status
                    // Note: This is a simplified version for your learning
                    withContext(Dispatchers.Main) {
                        _binding?.let {
                            adapter.updateData(cloudList)
                        }
                    }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}