package com.emotion.pet

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private val handler = Handler(Looper.getMainLooper())
    private var swapping = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBrainLabBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        binding.backBtn.setOnClickListener { finish() }

        adapter = AgentAdapter(Presets.PROVIDERS, prefs.aiProvider) { provider ->
            if (!swapping) playBrainSwap(provider)
        }
        binding.recycler.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        adapter.updateSelected(prefs.aiProvider)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    /**
     * Кратка "инсталация" на новия мозък — lift (250ms) → install glow pulse (~1.2s) →
     * финален pulse (~450ms) → fade out, доближава choreography-та от concept spec-а
     * (пълната conveyor-belt анимация не е практична за native Views без Android SDK за тест).
     */
    private fun playBrainSwap(provider: Presets.AiProvider) {
        swapping = true
        Haptics.bump(this)
        // веднага пазим избора — ако потребителят излезе преди анимацията да свърши
        // (Back, onDestroy), Peta пак трябва реално да е с новия мозък, не само визуално.
        prefs.aiProvider = provider.id
        adapter.updateSelected(provider.id)

        val overlay = binding.swapOverlay
        val glow = binding.swapGlow
        binding.swapGlyph.text = glyphFor(provider.id)
        binding.swapStatus.text = getString(R.string.brainlab_installing, provider.label)

        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(150L).start()

        glow.scaleX = 0.5f
        glow.scaleY = 0.5f
        glow.alpha = 0f
        glow.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250L)
            .withEndAction { pulseGlow(glow, 0) }
            .start()

        handler.postDelayed({
            glow.animate().scaleX(1.18f).scaleY(1.18f).setDuration(220L)
                .withEndAction { glow.animate().scaleX(1f).scaleY(1f).setDuration(220L).start() }
                .start()
            binding.swapStatus.text = getString(R.string.brainlab_ready)
            Haptics.bump(this)
        }, 250L + 1200L)

        handler.postDelayed({
            overlay.animate().alpha(0f).setDuration(220L)
                .withEndAction {
                    overlay.visibility = View.GONE
                    swapping = false
                }
                .start()
        }, 250L + 1200L + 480L)
    }

    private fun pulseGlow(view: View, step: Int) {
        if (step >= 3 || !swapping) return
        view.animate().alpha(0.55f).setDuration(200L)
            .withEndAction {
                view.animate().alpha(1f).setDuration(200L)
                    .withEndAction { pulseGlow(view, step + 1) }
                    .start()
            }
            .start()
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
