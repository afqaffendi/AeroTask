package com.example.classmatetaskshare.ui.gallery

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager // 1. Need this import
import com.example.classmatetaskshare.Assignment
import com.example.classmatetaskshare.R
import com.example.classmatetaskshare.databinding.FragmentGalleryBinding
import com.google.firebase.firestore.FirebaseFirestore

class GalleryFragment : Fragment(R.layout.fragment_gallery) {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var adapter: AssignmentAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentGalleryBinding.bind(view)

        // 2. IMPORTANT: You must tell the RecyclerView how to arrange items (List or Grid)
        binding.recyclerViewGallery.layoutManager = LinearLayoutManager(requireContext())

        // Initialize the adapter (Ensure your AssignmentAdapter class uses List<Assignment>)
        adapter = AssignmentAdapter(emptyList())
        binding.recyclerViewGallery.adapter = adapter

        fetchCloudData()
    }

    private fun fetchCloudData() {
        firestore.collection("assignments")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    // Use context? to prevent crashes if fragment is closed
                    context?.let {
                        Toast.makeText(it, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    return@addSnapshotListener
                }

                val assignmentList = mutableListOf<Assignment>()

                for (doc in snapshots!!) {
                    // 3. Update: Pull the 'sender' field so it doesn't show up as null
                    val task = Assignment(
                        subject = doc.getString("subject") ?: "",
                        title = doc.getString("title") ?: "",
                        deadline = doc.getString("deadline") ?: "",
                        sender = doc.getString("sender") ?: "Unknown"
                    )
                    assignmentList.add(task)
                }

                adapter.updateData(assignmentList)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}