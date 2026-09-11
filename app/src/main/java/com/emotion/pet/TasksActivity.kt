package com.emotion.pet

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ActivityTasksBinding
import com.emotion.pet.databinding.ItemTaskBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Списък със задачи — добави/отметни/изтрий, с по избор краен срок и локално напомняне. */
class TasksActivity : PetOverlayActivity() {

    private lateinit var binding: ActivityTasksBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: TaskAdapter
    private var pendingDueAt: Long? = null

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.backBtn.setOnClickListener { finish() }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter(
            onToggle = { id, done ->
                if (done) TaskReminders.cancel(this, id)
                refresh(TaskStore.setDone(prefs, id, done))
            },
            onDelete = { id ->
                TaskReminders.cancel(this, id)
                refresh(TaskStore.remove(prefs, id))
            }
        )
        binding.recycler.adapter = adapter

        binding.addBtn.setOnClickListener { submit() }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        binding.dueBtn.setOnClickListener { pickDueDate() }
        binding.dueClearBtn.setOnClickListener {
            pendingDueAt = null
            binding.dueRow.visibility = View.GONE
        }

        refresh(TaskStore.load(prefs))
    }

    private fun pickDueDate() {
        val now = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val cal = Calendar.getInstance().apply { set(year, month, day) }
                pickDueTime(cal)
            },
            now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun pickDueTime(cal: Calendar) {
        val now = Calendar.getInstance()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                pendingDueAt = cal.timeInMillis
                binding.dueText.text = "⏰ " + formatDue(cal.timeInMillis)
                binding.dueRow.visibility = View.VISIBLE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        this, Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true
        ).show()
    }

    private fun submit() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        binding.input.setText("")
        val due = pendingDueAt
        pendingDueAt = null
        binding.dueRow.visibility = View.GONE
        Haptics.tick(this)
        val list = TaskStore.add(prefs, text, due)
        if (due != null) TaskReminders.schedule(this, list.last())
        refresh(list)
    }

    private fun refresh(tasks: List<TaskStore.Task>) {
        val sorted = tasks.sortedBy { it.done }
        adapter.submit(sorted)
        binding.emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    companion object {
        fun formatDue(millis: Long): String =
            SimpleDateFormat("d MMM, HH:mm", Locale("bg")).format(java.util.Date(millis))
    }

    private class TaskAdapter(
        private val onToggle: (Long, Boolean) -> Unit,
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<TaskAdapter.VH>() {

        private val items = mutableListOf<TaskStore.Task>()

        fun submit(tasks: List<TaskStore.Task>) {
            items.clear()
            items.addAll(tasks)
            notifyDataSetChanged()
        }

        class VH(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = items[position]
            holder.binding.text.text = task.text
            holder.binding.doneCheck.setOnCheckedChangeListener(null)
            holder.binding.doneCheck.isChecked = task.done
            holder.binding.text.paintFlags = if (task.done) {
                holder.binding.text.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                holder.binding.text.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            holder.binding.text.alpha = if (task.done) 0.55f else 1f
            if (task.dueAt != null) {
                holder.binding.dueText.visibility = View.VISIBLE
                holder.binding.dueText.text = "⏰ " + formatDue(task.dueAt)
            } else {
                holder.binding.dueText.visibility = View.GONE
            }
            holder.binding.doneCheck.setOnCheckedChangeListener { _, checked ->
                onToggle(task.id, checked)
            }
            holder.binding.deleteBtn.setOnClickListener { onDelete(task.id) }
        }
    }
}
