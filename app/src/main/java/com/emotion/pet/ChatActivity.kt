package com.emotion.pet

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.emotion.pet.databinding.ActivityChatBinding

/** Чатът с любимеца — с истински ИИ (ако има ключ) или с вградената логика. */
class ChatActivity : PetOverlayActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var prefs: Prefs
    private val adapter = ChatAdapter()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = Prefs(this)

        val hasImage = prefs.spriteType == Prefs.TYPE_IMAGE && SpriteStore.exists(this)
        adapter.petBitmap = if (hasImage) SpriteStore.loadBitmap(this, 128) else null
        adapter.petEmoji = if (hasImage) null else prefs.emoji

        binding.nameText.text = prefs.petName
        binding.avatarEmoji.text = prefs.emoji.ifBlank { Presets.pet(prefs.petId).emoji }
        binding.subtitleText.text = if (prefs.aiKey.isBlank()) {
            getString(R.string.chat_offline_short)
        } else {
            Presets.provider(prefs.aiProvider).label
        }
        if (hasImage) {
            binding.avatarImage.visibility = View.VISIBLE
            binding.avatarEmoji.visibility = View.GONE
            binding.avatarImage.setImageBitmap(adapter.petBitmap)
        }
        binding.backBtn.setOnClickListener { finish() }
        binding.newChatBtn.setOnClickListener { clearChat() }

        binding.recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.recycler.adapter = adapter

        applyInsets()
        restoreHistory()
        buildQuickChips()

        binding.sendBtn.setOnClickListener { submit(binding.input.text.toString()) }
        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                submit(binding.input.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.statusSpacer) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.layoutParams.height = bars.top
            v.requestLayout()
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun restoreHistory() {
        val saved = ChatStore.load(prefs)
        adapter.setAll(saved.map { ChatAdapter.Row(it.role, it.content, ts = it.ts) })
        if (saved.isEmpty()) {
            val greeting = getString(R.string.chat_greeting, prefs.petName)
            val now = System.currentTimeMillis()
            adapter.add(ChatAdapter.Row(ChatStore.ROLE_PET, greeting, ts = now))
            ChatStore.append(prefs, ChatStore.Msg(ChatStore.ROLE_PET, greeting, now))
        }
        updateEmptyState()
        scrollToBottom()
    }

    private fun buildQuickChips() {
        val container = binding.chipsContainer
        container.removeAllViews()
        listOf(
            getString(R.string.chip_joke),
            getString(R.string.chip_howareyou),
            getString(R.string.chip_tip),
            getString(R.string.chip_game),
            getString(R.string.chip_feed)
        ).forEach { label ->
            val chip = Ui.chip(this, label)
            chip.setOnClickListener { submit(label) }
            container.addView(chip)
        }
    }

    private fun submit(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return
        binding.input.setText("")
        val index = adapter.add(ChatAdapter.Row(ChatStore.ROLE_USER, text))
        ChatStore.append(prefs, ChatStore.Msg(ChatStore.ROLE_USER, text))
        scrollToBottom()
        updateEmptyState()
        respond(text, index)
    }

    private fun respond(userText: String, userIndex: Int) {
        // "пише…" с анимирани точки
        val typingIndex = adapter.add(ChatAdapter.Row(ChatStore.ROLE_PET, "", typing = true))
        animateTyping(typingIndex)
        scrollToBottom()

        val key = prefs.aiKey
        val baseUrl = Presets.effectiveBaseUrl(prefs)
        val model = Presets.effectiveModel(prefs)

        if (key.isBlank() || baseUrl.isBlank() || model.isBlank()) {
            handler.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                val reply = SmallBrain.reply(this, userText, prefs)
                finishReply(typingIndex, reply, persist = true)
            }, 650L)
            return
        }

        val history = ChatStore.toApi(ChatStore.load(prefs))
        AiClient.chat(prefs.aiProvider, baseUrl, key, model, ChatStore.systemPrompt(this, prefs), history) { ok, text ->
            if (isFinishing || isDestroyed) return@chat
            if (ok) {
                finishReply(typingIndex, text, persist = true)
            } else {
                finishReply(typingIndex, getString(R.string.ai_test_fail, text), persist = false)
            }
        }
        binding.recycler.post { adapter.notifyItemChanged(userIndex) }
    }

    private val typingRunnable = object : Runnable {
        private var step = 0
        override fun run() {
            val index = adapter.indexOfTyping()
            if (index < 0) return
            step = (step + 1) % 4
            binding.recycler.post {
                val holder = binding.recycler.findViewHolderForAdapterPosition(index)
                if (holder is ChatAdapter.PetVH) {
                    holder.binding.dot1.alpha = if (step >= 1) 1f else 0.28f
                    holder.binding.dot2.alpha = if (step >= 2) 1f else 0.28f
                    holder.binding.dot3.alpha = if (step >= 3) 1f else 0.28f
                }
            }
            handler.postDelayed(this, 320L)
        }
    }

    private fun animateTyping(index: Int) {
        handler.removeCallbacks(typingRunnable)
        handler.postDelayed(typingRunnable, 300L)
    }

    private fun finishReply(index: Int, text: String, persist: Boolean) {
        handler.removeCallbacks(typingRunnable)
        adapter.update(index) {
            it.text = text
            it.typing = false
            it.ts = System.currentTimeMillis()
        }
        if (persist) {
            ChatStore.append(
                prefs,
                ChatStore.Msg(ChatStore.ROLE_PET, text, System.currentTimeMillis())
            )
        }
        scrollToBottom()
        updateEmptyState()
    }

    private fun clearChat() {
        ChatStore.clear(prefs)
        adapter.setAll(emptyList())
        val greeting = getString(R.string.chat_greeting, prefs.petName)
        adapter.add(ChatAdapter.Row(ChatStore.ROLE_PET, greeting))
        ChatStore.append(prefs, ChatStore.Msg(ChatStore.ROLE_PET, greeting))
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = adapter.itemCount == 0
        binding.emptyText.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) binding.emptyText.text = getString(R.string.chat_offline_hint)
    }

    private fun scrollToBottom() {
        if (adapter.itemCount == 0) return
        binding.recycler.post { binding.recycler.scrollToPosition(adapter.itemCount - 1) }
    }

    override fun onDestroy() {
        handler.removeCallbacks(typingRunnable)
        super.onDestroy()
    }
}
