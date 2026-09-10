package com.emotion.pet

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.emotion.pet.databinding.ItemMsgPetBinding
import com.emotion.pet.databinding.ItemMsgUserBinding

/** Списък със съобщенията: потребител отдясно, любимец отляво. */
class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class Row(
        val role: String,
        var text: String,
        var typing: Boolean = false
    )

    private val rows = mutableListOf<Row>()

    var petEmoji: String? = null
    var petBitmap: Bitmap? = null

    fun setAll(items: List<Row>) {
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    fun add(row: Row): Int {
        rows.add(row)
        notifyItemInserted(rows.size - 1)
        return rows.size - 1
    }

    fun update(index: Int, block: (Row) -> Unit) {
        if (index !in rows.indices) return
        block(rows[index])
        notifyItemChanged(index)
    }

    fun rowAt(index: Int): Row? = rows.getOrNull(index)

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position].role == ChatStore.ROLE_USER) TYPE_USER else TYPE_PET

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserVH(ItemMsgUserBinding.inflate(inflater, parent, false))
        } else {
            PetVH(ItemMsgPetBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        if (holder is UserVH) {
            holder.binding.text.text = row.text
        } else if (holder is PetVH) {
            holder.binding.text.text = row.text
            holder.binding.typing.visibility = if (row.typing) View.VISIBLE else View.GONE
            val bmp = petBitmap
            if (bmp != null) {
                holder.binding.avatarImage.visibility = View.VISIBLE
                holder.binding.avatarEmoji.visibility = View.GONE
                holder.binding.avatarImage.setImageBitmap(bmp)
            } else {
                holder.binding.avatarImage.visibility = View.GONE
                holder.binding.avatarEmoji.visibility = View.VISIBLE
                holder.binding.avatarEmoji.text = petEmoji ?: "🐾"
            }
        }
    }

    class UserVH(val binding: ItemMsgUserBinding) : RecyclerView.ViewHolder(binding.root)
    class PetVH(val binding: ItemMsgPetBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val TYPE_USER = 0
        private const val TYPE_PET = 1
    }
}
