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
import android.graphics.Color

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
        val currentUserId = auth.currentUser?.uid ?: return
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val userSpecificKey = "class_code_$currentUserId"

        val currentCode = sharedPref.getString(userSpecificKey, "this folder") ?: "this folder"

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.RoundedDialog)
            .setIcon(R.drawable.ic_folder)
            .setTitle("Leave Folder?")
            .setMessage("You won't see reminders for '$currentCode' until you join again.")
            .setPositiveButton("LEAVE") { _, _ ->
                // ONLY removes the folder for the logged-in user
                sharedPref.edit().remove(userSpecificKey).apply()
                updateClassUI()
                Toast.makeText(requireContext(), "You have left $currentCode", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("STAY", null)
            .show()

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setTextColor(Color.RED)
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.BLACK)
    }

    private fun updateClassUI() {
        val currentUserId = auth.currentUser?.uid ?: return
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // Fetch the code specific to THIS account
        val userSpecificKey = "class_code_$currentUserId"
        val savedCode = sharedPref.getString(userSpecificKey, null)

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
        val currentUserId = auth.currentUser?.uid ?: return
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)

        // Save the code using the unique Account Key
        val userSpecificKey = "class_code_$currentUserId"
        sharedPref.edit().putString(userSpecificKey, code).apply()

        updateClassUI()
        Toast.makeText(requireContext(), "Joined Folder: $code", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}