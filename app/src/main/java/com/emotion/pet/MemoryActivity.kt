package com.emotion.pet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ActivityMemoryBinding
import com.emotion.pet.databinding.ItemMemoryBinding

/** Каквото потребителят иска Peta да помни — прост списък със записки, изтриваеми поотделно. */
class MemoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: MemoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMemoryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.backBtn.setOnClickListener { finish() }
        binding.recycler.layoutManager = LinearLayoutManager(this)
        adapter = MemoryAdapter { id -> refresh(MemoryStore.remove(prefs, id)) }
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

        refresh(MemoryStore.load(prefs))
    }

    private fun submit() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        binding.input.setText("")
        Haptics.tick(this)
        refresh(MemoryStore.add(prefs, text))
    }

    private fun refresh(notes: List<MemoryStore.Note>) {
        val sorted = notes.sortedByDescending { it.createdAt }
        adapter.submit(sorted)
        binding.emptyText.visibility = if (sorted.isEmpty()) View.VISIBLE else View.GONE
    }

    private class MemoryAdapter(
        private val onDelete: (Long) -> Unit
    ) : RecyclerView.Adapter<MemoryAdapter.VH>() {

        private val items = mutableListOf<MemoryStore.Note>()

        fun submit(notes: List<MemoryStore.Note>) {
            items.clear()
            items.addAll(notes)
            notifyDataSetChanged()
        }

        class VH(val binding: ItemMemoryBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemMemoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val note = items[position]
            holder.binding.text.text = note.text
            holder.binding.deleteBtn.setOnClickListener { onDelete(note.id) }
        }
    }
}
