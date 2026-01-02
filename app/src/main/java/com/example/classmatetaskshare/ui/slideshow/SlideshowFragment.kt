package com.example.classmatetaskshare.ui.slideshow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.example.classmatetaskshare.LoginActivity
import com.example.classmatetaskshare.R
import com.example.classmatetaskshare.databinding.FragmentSlideshowBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SlideshowFragment : Fragment(R.layout.fragment_slideshow) {

    private var _binding: FragmentSlideshowBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentSlideshowBinding.bind(view)

        // Initial UI Setup
        updateClassUI()
        binding.tvProfileEmail.text = auth.currentUser?.email

        // 1. JOIN CLASS BUTTON
        binding.btnSaveClassCode.setOnClickListener {
            val newCode = binding.etClassCode.text.toString().trim().uppercase()
            if (newCode.isNotEmpty()) {
                saveClassCode(newCode)
            } else {
                Toast.makeText(context, "Please enter a class code", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. LEAVE CLASS BUTTON (With Confirmation Dialog)
        binding.btnLeaveClass.setOnClickListener {
            showLeaveConfirmation()
        }

        // 3. SIGN OUT BUTTON
        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    private fun updateClassUI() {
        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val savedCode = sharedPref.getString("class_code", null)

        if (savedCode != null) {
            // STATE: User is already in a class folder
            binding.tvCurrentClass.text = savedCode
            binding.cvClassStatus.visibility = View.VISIBLE // Show the "Active Class" card

            // Hide the Join inputs
            binding.etClassCode.visibility = View.GONE
            binding.btnSaveClassCode.visibility = View.GONE

            fetchUserTaskCount(savedCode)
        } else {
            // STATE: User has not joined a class yet
            binding.cvClassStatus.visibility = View.GONE

            // Show the Join inputs
            binding.etClassCode.visibility = View.VISIBLE
            binding.btnSaveClassCode.visibility = View.VISIBLE
            binding.etClassCode.setText("")
        }
    }

    private fun saveClassCode(code: String) {
        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("class_code", code).apply()

        Toast.makeText(context, "Joined Class: $code", Toast.LENGTH_SHORT).show()
        updateClassUI()
    }

    private fun showLeaveConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Leave Class Folder?")
            .setMessage("You will no longer see reminders for this class. You can rejoin anytime by entering the code again.")
            .setPositiveButton("Leave") { _, _ ->
                val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
                sharedPref.edit().remove("class_code").apply()

                Toast.makeText(context, "You have left the class", Toast.LENGTH_SHORT).show()
                updateClassUI()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun fetchUserTaskCount(code: String) {
        firestore.collection("assignments")
            .whereEqualTo("classCode", code)
            .get()
            .addOnSuccessListener { snapshot ->
                // Check _binding? to prevent the Line 21 NullPointer crash
                _binding?.tvTaskCount?.text = "Total Tasks in $code: ${snapshot.size()}"
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}