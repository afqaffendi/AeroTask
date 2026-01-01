package com.example.classmatetaskshare.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.classmatetaskshare.Assignment
import com.example.classmatetaskshare.R

// 1. Changed to use List<Assignment> to match your Fragment
class AssignmentAdapter(private var assignments: List<Assignment>) :
    RecyclerView.Adapter<AssignmentAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        // Ensure tvSender exists in your item_assignment.xml
        val tvSender: TextView = view.findViewById(R.id.tvSender)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_assignment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = assignments[position]
        // 2. Access properties directly from the Assignment object
        holder.tvSubject.text = item.subject
        holder.tvTitle.text = item.title
        holder.tvDeadline.text = "Due: ${item.deadline}"
        // If your Assignment class doesn't have 'sender' yet, you can comment this out
        // holder.tvSender.text = "Shared by: ${item.sender}"
    }

    override fun getItemCount() = assignments.size

    // 3. ADD THIS FUNCTION to fix the red error in GalleryFragment
    fun updateData(newList: List<Assignment>) {
        this.assignments = newList
        notifyDataSetChanged() // This tells the list to refresh on screen
    }
}