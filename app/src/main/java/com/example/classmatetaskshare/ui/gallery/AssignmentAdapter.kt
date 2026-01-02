package com.example.classmatetaskshare.ui.gallery

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.classmatetaskshare.Assignment
import com.example.classmatetaskshare.R
import com.google.android.material.card.MaterialCardView

class AssignmentAdapter(
    private var assignments: List<Assignment>,
    private val onFilterResult: (Boolean) -> Unit,
    private val onCheckedChange: (Assignment, Boolean) -> Unit,
    private val onDeleteTask: (Assignment, Int) -> Unit
) : RecyclerView.Adapter<AssignmentAdapter.ViewHolder>() {

    private var fullList: List<Assignment> = assignments

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvDeadline: TextView = view.findViewById(R.id.tvDeadline)
        val tvSubject: TextView = view.findViewById(R.id.tvSubject)
        val cbDone: CheckBox = view.findViewById(R.id.cbDone)
        val cardRoot: MaterialCardView = view.findViewById(R.id.cardAssignment)
        val ivShare: ImageView = view.findViewById(R.id.ivShare)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_assignment, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val task = assignments[position]
        holder.tvTitle.text = task.title
        holder.tvSubject.text = task.subject
        holder.tvDeadline.text = task.deadline

        val colors = listOf("#FFD571", "#A0E7E5", "#B4F8C8", "#FFAEBC", "#CFBAF0")
        val colorString = colors[position % colors.size]
        val backgroundColor = Color.parseColor(colorString)
        holder.cardRoot.setCardBackgroundColor(backgroundColor)

        holder.ivShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Task: ${task.title}\nDeadline: ${task.deadline}")
            }
            holder.itemView.context.startActivity(Intent.createChooser(shareIntent, "Share Task"))
        }

        holder.cardRoot.setOnLongClickListener {
            onDeleteTask(task, backgroundColor)
            true
        }

        holder.cbDone.setOnCheckedChangeListener(null)
        holder.cbDone.isChecked = task.isDone
        updateStrikeThrough(holder.tvTitle, task.isDone)

        holder.cbDone.setOnCheckedChangeListener { _, isChecked ->
            onCheckedChange(task, isChecked)
            updateStrikeThrough(holder.tvTitle, isChecked)
        }
    }

    private fun updateStrikeThrough(textView: TextView, isDone: Boolean) {
        textView.paintFlags = if (isDone) {
            textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    fun updateData(newList: List<Assignment>) {
        fullList = newList
        assignments = newList
        notifyDataSetChanged()
        onFilterResult(assignments.isEmpty())
    }

    fun filter(query: String) {
        assignments = if (query.isEmpty()) fullList else {
            fullList.filter { it.title.contains(query, true) || it.subject.contains(query, true) }
        }
        notifyDataSetChanged()
        onFilterResult(assignments.isEmpty())
    }

    // This is the function the Fragment is looking for
    fun getAssignmentAt(position: Int): Assignment {
        return assignments[position]
    }

    override fun getItemCount() = assignments.size
}