package com.example.classmatetaskshare.ui.gallery

import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.classmatetaskshare.Assignment
import com.example.classmatetaskshare.R
import com.google.firebase.auth.FirebaseAuth

class AssignmentAdapter(
    private var assignments: List<Assignment>,
    private val onFilterResult: (Boolean) -> Unit,
    private val onCheckedChange: (Assignment, Boolean) -> Unit // NEW: Callback for Checkbox
) : RecyclerView.Adapter<AssignmentAdapter.ViewHolder>() {

    private var fullList: List<Assignment> = assignments
    private val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        val tvSender: TextView = view.findViewById(R.id.tvSender)
        val cbDone: CheckBox = view.findViewById(R.id.cbDone) // NEW: Reference to CheckBox
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_assignment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = assignments[position]

        holder.tvSubject.text = item.subject
        holder.tvTitle.text = item.title
        holder.tvDeadline.text = "Due: ${item.deadline}"

        // 1. PERSONALIZATION LOGIC
        if (item.senderEmail == currentUserEmail) {
            holder.tvSender.text = "By: You"
            holder.tvSender.setTextColor(Color.parseColor("#6200EE"))
        } else {
            holder.tvSender.text = "By: ${item.sender}"
            holder.tvSender.setTextColor(Color.GRAY)
        }

        // 2. CHECKBOX LOGIC (Mark as Done)
        // Reset the listener to null first to avoid infinite loops when recycling views
        holder.cbDone.setOnCheckedChangeListener(null)
        holder.cbDone.isChecked = item.isDone

        // 3. STRIKE-THROUGH EFFECT
        updateStrikeThrough(holder.tvTitle, item.isDone)

        holder.cbDone.setOnCheckedChangeListener { _, isChecked ->
            // Update UI immediately for smoothness
            updateStrikeThrough(holder.tvTitle, isChecked)
            // Trigger the database update in Fragment
            onCheckedChange(item, isChecked)
        }
    }

    private fun updateStrikeThrough(textView: TextView, isDone: Boolean) {
        if (isDone) {
            textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            textView.setTextColor(Color.LTGRAY)
        } else {
            textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            textView.setTextColor(Color.BLACK)
        }
    }

    override fun getItemCount() = assignments.size

    fun getAssignmentAt(position: Int): Assignment = assignments[position]

    fun updateData(newList: List<Assignment>) {
        this.assignments = newList
        this.fullList = newList
        notifyDataSetChanged()
        onFilterResult(assignments.isEmpty())
    }

    fun filter(query: String) {
        val searchText = query.lowercase().trim()
        assignments = if (searchText.isEmpty()) {
            fullList
        } else {
            fullList.filter {
                it.subject.lowercase().contains(searchText) ||
                        it.title.lowercase().contains(searchText)
            }
        }
        notifyDataSetChanged()
        onFilterResult(assignments.isEmpty())
    }
}