package com.emotion.pet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ActivityBrainLabBinding
import com.emotion.pet.databinding.ItemAgentBinding

/**
 * "Един Peta, много мозъци" — избор на активния AI агент. Самите настройки
 * (API ключ, модел, base URL) си остават в AiSheet, отворен оттук с ⚙.
 */
class BrainLabActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBrainLabBinding
    private lateinit var prefs: Prefs
    private lateinit var adapter: AgentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrainLabBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.backBtn.setOnClickListener { finish() }

        adapter = AgentAdapter(Presets.PROVIDERS, prefs.aiProvider) { provider ->
            Haptics.bump(this)
            prefs.aiProvider = provider.id
            adapter.updateSelected(provider.id)
            Toast.makeText(this, getString(R.string.brainlab_switched, provider.label), Toast.LENGTH_SHORT)
                .show()
        }
        binding.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter.updateSelected(prefs.aiProvider)
    }

    /** Емоджи-глиф + кратка характеристика за всеки доставчик — без брандирани лога. */
    private fun glyphFor(id: String): String = when (id) {
        "openai" -> "🧠"
        "groq" -> "⚡"
        "openrouter" -> "🧭"
        "gemini" -> "✦"
        "claude" -> "📚"
        else -> "➕"
    }

    private fun taglineFor(id: String): String = when (id) {
        "openai" -> "Креативност · Общи задачи"
        "groq" -> "Светкавично бърз"
        "openrouter" -> "Отворен избор на модели"
        "gemini" -> "Проучване · Мултимодален"
        "claude" -> "Анализ · Писане"
        else -> "Свържи свой API"
    }

    private inner class AgentAdapter(
        private val providers: List<Presets.AiProvider>,
        private var selectedId: String,
        private val onSelect: (Presets.AiProvider) -> Unit
    ) : RecyclerView.Adapter<AgentAdapter.VH>() {

        inner class VH(val binding: ItemAgentBinding) : RecyclerView.ViewHolder(binding.root)

        fun updateSelected(id: String) {
            selectedId = id
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemAgentBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = providers.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val provider = providers[position]
            val active = provider.id == selectedId
            holder.binding.glyph.text = glyphFor(provider.id)
            holder.binding.label.text = provider.label
            holder.binding.tagline.text = taglineFor(provider.id)
            holder.binding.selectBtn.text =
                if (active) getString(R.string.brainlab_active) else getString(R.string.brainlab_select)
            holder.binding.selectBtn.alpha = if (active) 0.55f else 1f
            holder.binding.selectBtn.isEnabled = !active
            holder.binding.selectBtn.setOnClickListener { onSelect(provider) }
            holder.binding.card.setOnClickListener {
                if (active) {
                    if (supportFragmentManager.findFragmentByTag(AiSheet.TAG) == null) {
                        AiSheet().show(supportFragmentManager, AiSheet.TAG)
                    }
                } else {
                    onSelect(provider)
                }
            }
        }
    }
}
