package com.emotion.pet

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ItemPetBinding
import com.emotion.pet.databinding.SheetPetsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Избор на любимец: галерия от готови character-и с характер и акцент,
 * плюс опция за собствен образ от галерията.
 */
class PetPickerSheet : BottomSheetDialogFragment() {

    interface Listener {
        fun onPetChosen(pet: Presets.Pet)
        fun onPickImage()
    }

    private var _binding: SheetPetsBinding? = null
    private val binding get() = _binding!!
    private val listener: Listener? get() = activity as? Listener

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SheetPetsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val prefs = Prefs(requireContext())

        binding.pets.adapter = PetAdapter(Presets.PETS) { pet ->
            prefs.petId = pet.id
            if (!prefs.nameCustomized) {
                // името следва любимеца, освен ако потребителят не е избрал свое
                prefs.nameCustomized = false
            }
            prefs.emoji = pet.emoji
            prefs.emojiCustomized = false
            prefs.spriteType = Prefs.TYPE_EMOJI
            SpriteStore.clear(requireContext())
            listener?.onPetChosen(pet)
            dismiss()
        }
        binding.pets.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.uploadCard.setOnClickListener {
            listener?.onPickImage()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val sheet = (dialog as? BottomSheetDialog)
            ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) ?: return
        val height = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        sheet.layoutParams?.let { lp -> lp.height = height; sheet.layoutParams = lp }
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

    /** Картичка за всеки любимец — емоджи, име и характер. */
    private class PetAdapter(
        private val pets: List<Presets.Pet>,
        private val onClick: (Presets.Pet) -> Unit
    ) : RecyclerView.Adapter<PetAdapter.VH>() {

        class VH(val binding: ItemPetBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemPetBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = pets.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val pet = pets[position]
            val b = holder.binding
            b.petEmoji.text = pet.emoji
            b.petName.text = pet.name
            b.petTagline.text = pet.tagline
            b.petCard.setCardBackgroundColor(blend(pet.accent, 0x24))
            b.petCard.strokeColor = withAlpha(pet.accent, 0.45f)
            b.petEmojiBg.background?.mutate()?.setTint(withAlpha(pet.accent, 0.22f))
            b.petCard.setOnClickListener { onClick(pet) }
        }

        private fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((255 * alpha).toInt(), Color.red(color), Color.green(color), Color.blue(color))

        private fun blend(color: Int, alpha: Int): Int =
            Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    companion object {
        const val TAG = "pet_picker"
    }
}
