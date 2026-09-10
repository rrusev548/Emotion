package com.emotion.pet

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import com.emotion.pet.databinding.SheetAiBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Настройки на ИИ: доставчик, модел, ключ. Ключът се пази локално
 * в SharedPreferences и се използва директно от приложението.
 */
class AiSheet : BottomSheetDialogFragment() {

    private var _binding: SheetAiBinding? = null
    private val binding get() = _binding!!
    private var providerId: String = "openai"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetAiBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())
        providerId = prefs.aiProvider

        buildProviderChips()

        binding.modelInput.setText(prefs.aiModel)
        binding.baseUrlInput.setText(prefs.aiBaseUrl)
        binding.keyInput.setText(prefs.aiKey)
        binding.personalityInput.setText(prefs.aiPersonality)
        updateProviderUi(prefs.petName)

        binding.toggleKeyBtn.setOnClickListener {
            val visible = binding.keyInput.inputType ==
                (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
            binding.keyInput.inputType = if (visible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            }
            binding.keyInput.setSelection(binding.keyInput.text.length)
        }

        binding.getKeyBtn.setOnClickListener {
            val url = Presets.provider(providerId).keyUrl
            if (url.isBlank()) {
                toast(getString(R.string.ai_no_key_url))
            } else {
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }

        binding.saveBtn.setOnClickListener {
            if (save(prefs)) {
                toast(getString(R.string.ai_saved))
                refreshSummary(prefs)
            }
        }

        binding.testBtn.setOnClickListener {
            if (!save(prefs)) return@setOnClickListener
            refreshSummary(prefs)
            val base = Presets.effectiveBaseUrl(prefs)
            val model = Presets.effectiveModel(prefs)
            if (prefs.aiKey.isBlank()) {
                showResult(getString(R.string.ai_no_key_title), false)
                return@setOnClickListener
            }
            binding.testResult.visibility = View.VISIBLE
            binding.testResult.text = getString(R.string.ai_testing)
            binding.testBtn.isEnabled = false
            AiClient.test(
                base,
                prefs.aiKey,
                model,
                ChatStore.systemPrompt(requireContext(), prefs)
            ) { ok, text ->
                binding.testBtn.isEnabled = true
                showResult(if (ok) getString(R.string.ai_test_ok) else text, ok)
            }
        }

        binding.clearChatBtn.setOnClickListener {
            ChatStore.clear(prefs)
            toast(getString(R.string.ai_clear_chat))
        }
    }

    private fun buildProviderChips() {
        binding.providerRow.removeAllViews()
        Presets.PROVIDERS.forEach { provider ->
            val chip = Ui.chip(requireContext(), provider.label)
            chip.isSelected = provider.id == providerId
            chip.setOnClickListener {
                providerId = provider.id
                buildProviderChips()
                val prefs = Prefs(requireContext())
                // ако полето е празно или е било подразбиращото се за друг доставчик → сложи новото
                val current = binding.modelInput.text.toString().trim()
                val wasAnyDefault = Presets.PROVIDERS.any { it.defaultModel == current } || current.isEmpty()
                if (wasAnyDefault) {
                    binding.modelInput.setText(provider.defaultModel)
                }
                updateProviderUi(prefs.petName)
            }
            binding.providerRow.addView(chip)
        }
    }

    private fun updateProviderUi(petName: String) {
        val provider = Presets.provider(providerId)
        binding.baseUrlGroup.visibility =
            if (providerId == "custom") View.VISIBLE else View.GONE
        binding.modelInput.hint = provider.defaultModel.ifBlank { "напр. llama3.1" }
        binding.freeModelBtn.visibility =
            if (provider.freeModel != null) View.VISIBLE else View.GONE
        provider.freeModel?.let { free ->
            binding.freeModelBtn.text = getString(R.string.ai_free_model, free)
            binding.freeModelBtn.setOnClickListener { binding.modelInput.setText(free) }
        }
        val model = binding.modelInput.text.toString().ifBlank { provider.defaultModel }
        val label = if (providerId == "custom") {
            binding.baseUrlInput.text.toString().ifBlank { "Custom" }
        } else {
            "${provider.label} · $model"
        }
        binding.currentText.text = getString(R.string.ai_current, petName, label)
    }

    private fun save(prefs: Prefs): Boolean {
        val provider = Presets.provider(providerId)
        val base = binding.baseUrlInput.text.toString().trim()
        if (providerId == "custom" && base.isBlank()) {
            toast(getString(R.string.ai_base_required))
            return false
        }
        prefs.aiProvider = providerId
        prefs.aiModel = binding.modelInput.text.toString().trim().ifBlank { provider.defaultModel }
        prefs.aiKey = binding.keyInput.text.toString().trim()
        prefs.aiBaseUrl = base
        prefs.aiPersonality = binding.personalityInput.text.toString().trim()
        return true
    }

    private fun refreshSummary(prefs: Prefs) {
        val provider = Presets.provider(prefs.aiProvider)
        binding.currentText.text = getString(
            R.string.ai_current,
            prefs.petName + " · " + provider.label,
            Presets.effectiveModel(prefs)
        )
    }

    private fun showResult(text: String, ok: Boolean) {
        binding.testResult.visibility = View.VISIBLE
        binding.testResult.text = text
        binding.testResult.setTextColor(
            requireContext().getColor(if (ok) R.color.accent else R.color.accent_dark)
        )
    }

    private fun toast(text: String) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val height = (resources.displayMetrics.heightPixels * 0.88f).toInt()
        sheet.layoutParams?.let { lp ->
            lp.height = height
            sheet.layoutParams = lp
        }
        BottomSheetBehavior.from(sheet).apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isHideable = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // кажи на менюто, че нещата са се променили
        runCatching { parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf()) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ai_sheet"
        const val RESULT_KEY = "ai_changed"
    }
}
