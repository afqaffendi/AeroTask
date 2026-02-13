package com.example.classmatetaskshare.ui.gallery

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.classmatetaskshare.AppDatabase
import com.example.classmatetaskshare.Assignment
import com.example.classmatetaskshare.R
import com.example.classmatetaskshare.databinding.FragmentGalleryBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GalleryFragment : Fragment(R.layout.fragment_gallery) {
    private var _binding: FragmentGalleryBinding? = null
    // FIXED: Nullable binding prevents the crash at line 29
    private val binding get() = _binding

    private lateinit var adapter: AssignmentAdapter
    private var snapshotListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // FIXED: Uses Singleton pattern to stop "Excessive Binder Traffic"
    private val dbLocal by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGalleryBinding.bind(view)

        setupRecyclerView()
        setupNavigation()
        setupSwipeToDelete() // Restored your old swipe logic
        fetchCloudData()
    }

    private fun setupRecyclerView() {
        // FIXED: Passing all 4 parameters to resolve build errors
        adapter = AssignmentAdapter(
            assignments = emptyList(),
            onFilterResult = { isEmpty ->
                // Safe check prevents crash when data arrives after leaving screen
                binding?.tvEmptyState?.visibility = if (isEmpty) View.VISIBLE else View.GONE
            },
            onCheckedChange = { assignment, isChecked ->
                updateLocalProgress(assignment, isChecked)
            },
            onDeleteTask = { task, _ ->
                if (task.senderEmail == auth.currentUser?.email) {
                    showDeleteConfirmation(task)
                } else {
                    Toast.makeText(requireContext(), "Only owners can delete tasks", Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding?.recyclerViewGallery?.layoutManager = LinearLayoutManager(requireContext())
        binding?.recyclerViewGallery?.adapter = adapter
    }

    private fun setupSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val task = adapter.getAssignmentAt(position)

                if (task.senderEmail == auth.currentUser?.email) {
                    showDeleteConfirmation(task, position)
                } else {
                    // Bounce back if not the owner
                    adapter.notifyItemChanged(position)
                    Toast.makeText(requireContext(), "Only the creator can delete this", Toast.LENGTH_SHORT).show()
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding?.recyclerViewGallery)
    }

    private fun showDeleteConfirmation(task: Assignment, position: Int? = null) {
        // Restored your old dialog style and colors
        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.RoundedDialog)
            .setIcon(R.drawable.ic_menu_delete)
            .setTitle("Delete Task")
            .setMessage("Are you sure you want to remove '${task.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("assignments").document(task.docId).delete()
                    .addOnSuccessListener { Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show() }
            }
            .setNegativeButton("Cancel") { d, _ ->
                position?.let { adapter.notifyItemChanged(it) }
                d.dismiss()
            }
            .show()

        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(Color.RED)
    }

    private fun setupNavigation() {
        binding?.btnNavHome?.setOnClickListener { findNavController().navigate(R.id.nav_home) }
        binding?.btnCreateTask?.setOnClickListener { findNavController().navigate(R.id.nav_home) }
        binding?.btnNavChat?.setOnClickListener { findNavController().navigate(R.id.nav_slideshow) }
    }

    private fun fetchCloudData() {
        val userId = auth.currentUser?.uid ?: return
        val prefs = requireContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val classCode = prefs.getString("class_code_$userId", "PRIVATE") ?: "PRIVATE"

        snapshotListener = firestore.collection("assignments")
            .whereEqualTo("classCode", classCode)
            .addSnapshotListener { snapshots, _ ->
                // CRITICAL: Prevent crash if fragment view is destroyed
                if (_binding == null) return@addSnapshotListener

                val cloudList = snapshots?.map { doc ->
                    Assignment(
                        docId = doc.id,
                        subject = doc.getString("subject") ?: "",
                        title = doc.getString("title") ?: "",
                        deadline = doc.getString("deadline") ?: "",
                        senderEmail = doc.getString("senderEmail") ?: "",
                        classCode = doc.getString("classCode") ?: "",
                        isDone = doc.getBoolean("isDone") ?: false
                    )
                } ?: emptyList()

                adapter.updateData(cloudList)
            }
    }

    private fun updateLocalProgress(task: Assignment, isDone: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            dbLocal.assignmentDao().update(task.copy(isDone = isDone))
        }
    }

    override fun onDestroyView() {
        // FIXED: Stop the cloud listener to prevent memory leaks/crashes
        snapshotListener?.remove()
        super.onDestroyView()
        _binding = null
    }
}