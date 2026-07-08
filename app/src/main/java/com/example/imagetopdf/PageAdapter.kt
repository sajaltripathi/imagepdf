package com.example.imagetopdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.imagetopdf.databinding.ItemPageBinding

/**
 * Shows the currently selected images as an ordered list of PDF pages.
 * Thumbnails are decoded at a small, downsampled size purely for on-screen
 * preview; the full-resolution image is only decoded later, at conversion
 * time, directly from the original Uri.
 */
class PageAdapter(
    private val pages: MutableList<Uri>,
    private val onMoveUp: (Int) -> Unit,
    private val onMoveDown: (Int) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<PageAdapter.PageViewHolder>() {

    inner class PageViewHolder(val binding: ItemPageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val binding = ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val uri = pages[position]
        val binding = holder.binding
        val context = binding.root.context

        binding.textPageNumber.text = context.getString(R.string.page_number, position + 1)

        binding.imageThumbnail.tag = uri
        binding.imageThumbnail.setImageBitmap(null)
        val thumbnail = loadThumbnail(context, uri, THUMBNAIL_TARGET_PX)
        if (binding.imageThumbnail.tag == uri) {
            binding.imageThumbnail.setImageBitmap(thumbnail)
        }

        binding.buttonMoveUp.isEnabled = position > 0
        binding.buttonMoveDown.isEnabled = position < pages.size - 1
        binding.buttonMoveUp.alpha = if (position > 0) 1f else 0.3f
        binding.buttonMoveDown.alpha = if (position < pages.size - 1) 1f else 0.3f

        binding.buttonMoveUp.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) onMoveUp(adapterPos)
        }
        binding.buttonMoveDown.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) onMoveDown(adapterPos)
        }
        binding.buttonRemove.setOnClickListener {
            val adapterPos = holder.bindingAdapterPosition
            if (adapterPos != RecyclerView.NO_POSITION) onRemove(adapterPos)
        }
    }

    override fun getItemCount(): Int = pages.size

    private fun loadThumbnail(context: Context, uri: Uri, targetPx: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            var sampleSize = 1
            val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
            if (maxDim > 0) {
                while (maxDim / sampleSize > targetPx * 2) {
                    sampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            context.contentResolver.openInputStream(uri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        private const val THUMBNAIL_TARGET_PX = 160
    }
}
