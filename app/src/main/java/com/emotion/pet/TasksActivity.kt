package com.emotion.pet

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ActivityTasksBinding
import com.emotion.pet.databinding.ItemTaskBinding

/** Прост списък със задачи — без напомняния, само добави/отметни/изтрий. */
class TasksActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTasksBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: TaskAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTasksBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.backBtn.setOnClickListener { finish() }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = TaskAdapter(
            onToggle = { id, done -> refresh(TaskStore.setDone(prefs, id, done)) },
            onDelete = { id -> refresh(TaskStore.remove(prefs, id)) }
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

        refresh(TaskStore.load(prefs))
    }

    private fun submit() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        binding.input.setText("")
        Haptics.tick(this)
        refresh(TaskStore.add(prefs, text))
    }

    private fun refresh(tasks: List<TaskStore.Task>) {
        val sorted = tasks.sortedBy { it.done }
        adapter.submit(sorted)
        binding.emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
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
            holder.binding.doneCheck.setOnCheckedChangeListener { _, checked ->
                onToggle(task.id, checked)
            }
            holder.binding.deleteBtn.setOnClickListener { onDelete(task.id) }
        }
    }
}
