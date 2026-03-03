package com.example.securevaultcamera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PhotoAdapter(
    private val onViewClicked: (EncryptedPhoto) -> Unit
) : RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder>() {

    private val items = mutableListOf<EncryptedPhoto>()

    fun submitList(newItems: List<EncryptedPhoto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_photo, parent, false)
        return PhotoViewHolder(view, onViewClicked)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class PhotoViewHolder(
        itemView: View,
        private val onViewClicked: (EncryptedPhoto) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val labelText: TextView = itemView.findViewById(R.id.photoName)
        private val viewButton: Button = itemView.findViewById(R.id.viewButton)

        fun bind(photo: EncryptedPhoto) {
            labelText.text = photo.label
            viewButton.setOnClickListener { onViewClicked(photo) }
        }
    }
}
