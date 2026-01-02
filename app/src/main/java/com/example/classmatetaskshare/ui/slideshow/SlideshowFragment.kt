package com.example.classmatetaskshare.ui.slideshow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.classmatetaskshare.LoginActivity
import com.example.classmatetaskshare.R
import com.example.classmatetaskshare.databinding.FragmentSlideshowBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        updateClassUI()
        binding.tvProfileEmail.text = auth.currentUser?.email

        binding.btnSaveClassCode.setOnClickListener {
            val code = binding.etClassCode.text.toString().trim().uppercase()
            if (code.isNotEmpty()) saveClassCode(code)
        }

        binding.btnLeaveClass.setOnClickListener { showLeaveConfirmation() }

        binding.btnSignOut.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            requireActivity().finish()
        }
    }

    private fun showLeaveConfirmation() {
        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        // FIXED: Define the variable to solve the red error
        val currentCode = sharedPref.getString("class_code", "this folder") ?: "this folder"

        MaterialAlertDialogBuilder(requireContext(), R.style.CustomMaterialDialog)
            .setTitle("Leave Folder?")
            .setMessage("You won't see reminders for $currentCode until you join again.")
            .setPositiveButton("Leave") { _, _ ->
                sharedPref.edit().remove("class_code").apply()
                updateClassUI()
            }
            .setNegativeButton("Stay", null)
            .show()
    }

    private fun updateClassUI() {
        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        val savedCode = sharedPref.getString("class_code", null)

        if (savedCode != null) {
            binding.tvCurrentClass.text = "Active Folder: $savedCode"
            binding.cvClassStatus.visibility = View.VISIBLE
            binding.etClassCode.visibility = View.GONE
            binding.btnSaveClassCode.visibility = View.GONE
        } else {
            binding.cvClassStatus.visibility = View.GONE
            binding.etClassCode.visibility = View.VISIBLE
            binding.btnSaveClassCode.visibility = View.VISIBLE
        }
    }

    private fun saveClassCode(code: String) {
        val sharedPref = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        sharedPref.edit().putString("class_code", code).apply()
        updateClassUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}