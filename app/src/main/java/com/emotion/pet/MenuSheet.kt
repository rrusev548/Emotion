package com.emotion.pet

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ItemEmojiBinding
import com.emotion.pet.databinding.SheetMenuBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Менюто: грижа, образ, стая, ИИ и настройки. */
class MenuSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onCare(action: String)
        fun onAppearanceChanged()
        fun onPickImage()
        fun onPickWallpaper()
        fun onClearWallpaper()
        fun onOpenAi()
        fun onKeepAwake(enabled: Boolean)
        fun onResetPet()
        fun onRename()
    }

    private var _binding: SheetMenuBinding? = null
    private val binding get() = _binding!!
    private val listener: Listener? get() = activity as? Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetMenuBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())

        refreshHeader(prefs)

        parentFragmentManager.setFragmentResultListener(
            AiSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ -> refreshAiStatus(Prefs(requireContext())) }

        binding.renameBtn.setOnClickListener { listener?.onRename() }
        binding.feedBtn.setOnClickListener { listener?.onCare("feed") }
        binding.playBtn.setOnClickListener { listener?.onCare("play") }
        binding.sleepBtn.setOnClickListener { listener?.onCare("sleep") }
        binding.petBtn.setOnClickListener { listener?.onCare("pet") }

        // ---- образ ----
        binding.sizeSeek.max = Prefs.MAX_SIZE - Prefs.MIN_SIZE
        binding.sizeSeek.progress = prefs.sizeDp - Prefs.MIN_SIZE
        binding.sizeValue.text = getString(R.string.size_value, prefs.sizeDp)
        binding.sizeSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val size = Prefs.MIN_SIZE + progress
                prefs.sizeDp = size
                binding.sizeValue.text = getString(R.string.size_value, size)
                listener?.onAppearanceChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.speedSeek.max = 170
        binding.speedSeek.progress = ((prefs.speed * 100f).toInt() - 30).coerceIn(0, 170)
        binding.speedValue.text = getString(R.string.speed_value, prefs.speed)
        binding.speedSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val speed = (progress + 30) / 100f
                prefs.speed = speed
                binding.speedValue.text = getString(R.string.speed_value, speed)
                listener?.onAppearanceChanged()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        binding.mirrorSwitch.isChecked = prefs.mirrored
        binding.mirrorSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.mirrored = checked
            listener?.onAppearanceChanged()
        }

        val emojiAdapter = EmojiAdapter(Presets.EMOJIS, prefs.emoji) { emoji ->
            prefs.spriteType = Prefs.TYPE_EMOJI
            prefs.emoji = emoji
            listener?.onAppearanceChanged()
        }
        binding.emojiList.layoutManager =
            LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.emojiList.adapter = emojiAdapter

        binding.uploadBtn.setOnClickListener { listener?.onPickImage() }

        // ---- стая ----
        val paletteButtons = mapOf(
            Presets.Palette.NIGHT to binding.roomNight,
            Presets.Palette.DAWN to binding.roomDawn,
            Presets.Palette.MINT to binding.roomMint
        )
        fun refreshPalette() {
            val current = Presets.Palette.of(prefs.palette)
            paletteButtons.forEach { (palette, view) ->
                view.isSelected = palette == current
            }
        }
        paletteButtons.forEach { (palette, view) ->
            view.setOnClickListener {
                prefs.palette = palette.name
                refreshPalette()
                listener?.onAppearanceChanged()
            }
        }
        refreshPalette()

        binding.wallpaperBtn.setOnClickListener { listener?.onPickWallpaper() }
        binding.wallpaperClearBtn.setOnClickListener {
            refreshPalette()
            listener?.onClearWallpaper()
        }

        // ---- ИИ ----
        refreshAiStatus(prefs)
        binding.aiBtn.setOnClickListener { listener?.onOpenAi() }

        // ---- други ----
        binding.keepAwakeSwitch.isChecked = prefs.keepAwake
        binding.keepAwakeSwitch.setOnCheckedChangeListener { _, checked ->
            listener?.onKeepAwake(checked)
        }
        binding.resetBtn.setOnClickListener { listener?.onResetPet() }
    }

    override fun onResume() {
        super.onResume()
        refreshHeader(Prefs(requireContext()))
        refreshAiStatus(Prefs(requireContext()))
    }

    private fun refreshHeader(prefs: Prefs) {
        binding.nameText.text = prefs.petName
        binding.statsText.text =
            getString(R.string.stats_format, prefs.fullness, prefs.energy, prefs.mood)
        binding.sleepBtn.text =
            getString(if (prefs.sleeping) R.string.action_wake else R.string.action_sleep)
    }

    private fun refreshAiStatus(prefs: Prefs) {
        val provider = Presets.provider(prefs.aiProvider)
        binding.aiStatus.text = if (prefs.aiKey.isBlank()) {
            getString(R.string.ai_no_key_title)
        } else {
            getString(R.string.ai_current, provider.label, Presets.effectiveModel(prefs))
        }
    }

    override fun onStart() {
        super.onStart()
        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val height = (resources.displayMetrics.heightPixels * 0.86f).toInt()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /** Хоризонтален списък с готовите образи. */
    private class EmojiAdapter(
        private val emojis: List<String>,
        private val selected: String,
        private val onClick: (String) -> Unit
    ) : RecyclerView.Adapter<EmojiAdapter.VH>() {

        class VH(val binding: ItemEmojiBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemEmojiBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = emojis.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val emoji = emojis[position]
            holder.binding.emoji.text = emoji
            holder.binding.emoji.isSelected = emoji == selected
            holder.binding.emoji.setOnClickListener { onClick(emoji) }
        }
    }

    companion object {
        const val TAG = "menu_sheet"

        @Suppress("unused")
        fun dp(context: Context, value: Int): Int = Ui.dp(context, value)
    }
}
