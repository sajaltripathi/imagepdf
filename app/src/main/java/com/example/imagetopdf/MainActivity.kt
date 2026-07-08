package com.example.imagetopdf

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.imagetopdf.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Image to PDF
 *
 * Everything happens in memory for the lifetime of a single conversion:
 *  - Images are only ever accessed through Uris granted by the system picker
 *    (Storage Access Framework). No permission for broad storage access is
 *    requested, and no image bytes are ever written to app-private/cache
 *    storage.
 *  - There is no networking code anywhere in this app, and the manifest
 *    declares no INTERNET permission, so no network request is possible.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** Ordered list of page image Uris, in the order they will appear in the PDF. */
    private val pages = mutableListOf<Uri>()
    private lateinit var adapter: PageAdapter

    // Single picker flow used for both a single image and multiple images.
    private val pickImages =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                for (uri in uris) {
                    try {
                        // Keep read access for as long as we hold the Uri; safe to
                        // call even for a single selected item.
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: SecurityException) {
                        // Some providers don't support persistable grants; the Uri
                        // is still readable for the remainder of this session.
                    }
                    pages.add(uri)
                }
                adapter.notifyDataSetChanged()
                updateEmptyState()
            }
        }

    // System "Save As" dialog for the resulting PDF.
    private val createPdfDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                writePdfToUri(uri)
            } else {
                setConverting(false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PageAdapter(
            pages = pages,
            onMoveUp = { position -> movePage(position, position - 1) },
            onMoveDown = { position -> movePage(position, position + 1) },
            onRemove = { position -> removePage(position) }
        )
        binding.recyclerViewPages.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPages.adapter = adapter

        binding.buttonSelectImages.setOnClickListener {
            pickImages.launch(arrayOf("image/*"))
        }

        binding.buttonConvert.setOnClickListener {
            startConversion()
        }

        updateEmptyState()
    }

    private fun movePage(from: Int, to: Int) {
        if (to < 0 || to >= pages.size) return
        val item = pages.removeAt(from)
        pages.add(to, item)
        adapter.notifyItemMoved(from, to)
        val start = minOf(from, to)
        val count = kotlin.math.abs(from - to) + 1
        adapter.notifyItemRangeChanged(start, count)
    }

    private fun removePage(position: Int) {
        if (position < 0 || position >= pages.size) return
        pages.removeAt(position)
        adapter.notifyItemRemoved(position)
        if (position < pages.size) {
            adapter.notifyItemRangeChanged(position, pages.size - position)
        }
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val hasPages = pages.isNotEmpty()
        binding.textEmptyState.visibility = if (hasPages) View.GONE else View.VISIBLE
        binding.recyclerViewPages.visibility = if (hasPages) View.VISIBLE else View.GONE
        binding.buttonConvert.isEnabled = hasPages
    }

    private fun startConversion() {
        if (pages.isEmpty()) return
        setConverting(true)
        val fileName = "converted_${System.currentTimeMillis()}.pdf"
        // Ask where to save first; if the user cancels, no conversion work is done.
        createPdfDocument.launch(fileName)
    }

    private fun setConverting(converting: Boolean) {
        binding.progressBar.visibility = if (converting) View.VISIBLE else View.GONE
        binding.buttonConvert.isEnabled = !converting && pages.isNotEmpty()
        binding.buttonSelectImages.isEnabled = !converting
    }

    private fun writePdfToUri(uri: Uri) {
        lifecycleScope.launch {
            var document: PdfDocument? = null
            try {
                document = withContext(Dispatchers.IO) { buildPdfDocument() }
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        document.writeTo(out)
                    } ?: throw IllegalStateException("Unable to open output stream")
                }
                Toast.makeText(this@MainActivity, getString(R.string.pdf_saved), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.pdf_error, e.message ?: e.javaClass.simpleName),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                document?.close()
                setConverting(false)
            }
        }
    }

    /**
     * Builds a multi-page PDF in memory, one page per selected image, in the
     * order currently shown to the user. Each page is sized to exactly match
     * the source image's pixel dimensions, and the full-resolution decoded
     * bitmap is drawn onto the page with no scaling or re-encoding, so no
     * image quality is lost in the conversion.
     */
    private fun buildPdfDocument(): PdfDocument {
        val document = PdfDocument()
        for ((index, uri) in pages.withIndex()) {
            val bitmap = loadFullResolutionBitmap(uri) ?: continue
            try {
                val pageInfo = PdfDocument.PageInfo
                    .Builder(bitmap.width, bitmap.height, index + 1)
                    .create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
            } finally {
                bitmap.recycle()
            }
        }
        return document
    }

    /**
     * Decodes the image at full pixel resolution (no inSampleSize downscaling)
     * and applies EXIF rotation/flip if present so the page appears the right
     * way up. This is a lossless pixel remap, not a re-compression.
     */
    private fun loadFullResolutionBitmap(uri: Uri): Bitmap? {
        val orientation = readExifOrientation(uri)

        val original: Bitmap = contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input)
        } ?: return null

        if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) {
            return original
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return original
        }

        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        if (rotated !== original) {
            original.recycle()
        }
        return rotated
    }

    private fun readExifOrientation(uri: Uri): Int {
        return try {
            contentResolver.openInputStream(uri)?.use { input: InputStream ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
    }
}
