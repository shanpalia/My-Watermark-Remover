package com.shanpalia.watermarkremover

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private var sourceBitmap: Bitmap? = null
    private var resultBitmap: Bitmap? = null
    private lateinit var preview: ImageView
    private lateinit var maskView: MaskDrawingView
    private lateinit var status: TextView
    private lateinit var removeButton: Button
    private var openCvReady = false

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openCvReady = OpenCVLoader.initLocal()
        buildUi()
        status.text = if (openCvReady) "Choose an image, paint over the watermark, then tap Remove."
        else "Image engine failed to load. Please check build logs."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(16))
            setBackgroundColor(Color.rgb(255, 255, 255))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(this).apply {
            text = "My Watermark Remover"
            textSize = 25f
            setTextColor(Color.rgb(0, 145, 105))
            setPadding(0, 0, 0, dp(8))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        content.addView(TextView(this).apply {
            text = "by PaliaAPK HUB  |  Developer By shanpalia\nOffline Image Cleanup"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            setPadding(0, 0, 0, dp(12))
        })
        content.addView(Button(this).apply {
            text = "Choose Image"
            setOnClickListener { pickImage.launch("image/*") }
        })
        val frame = android.widget.FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
            contentDescription = "Selected image preview"
        }
        maskView = MaskDrawingView(this).apply { isEnabled = false }
        frame.addView(preview, android.widget.FrameLayout.LayoutParams(-1, dp(360)))
        frame.addView(maskView, android.widget.FrameLayout.LayoutParams(-1, dp(360)))
        content.addView(frame)
        content.addView(TextView(this).apply {
            text = "Draw over the watermark with your finger. Use Reset Mask if needed."
            textSize = 13f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(10), 0, dp(4))
        })
        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(Button(this).apply {
            text = "Reset Mask"
            setOnClickListener { maskView.clearMask() }
        }, LinearLayout.LayoutParams(0, dp(52), 1f))
        removeButton = Button(this).apply {
            text = "Remove"
            isEnabled = false
            setOnClickListener { removeWatermark() }
        }
        buttons.addView(removeButton, LinearLayout.LayoutParams(0, dp(52), 1f))
        content.addView(buttons)
        content.addView(Button(this).apply {
            text = "Save Result"
            setOnClickListener { saveResult() }
        })
        status = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.rgb(45, 65, 65))
            setPadding(0, dp(10), 0, 0)
        }
        content.addView(status)
        content.addView(TextView(this).apply {
            text = "Note: results depend on the selected area. This starter uses classical inpainting, not neural AI. Only edit content you own or have permission to edit."
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, dp(12), 0, dp(10))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadImage(uri: Uri) {
        try {
            val original = contentResolver.openInputStream(uri).use { input ->
                BitmapFactory.decodeStream(input)
            } ?: throw IllegalArgumentException("Could not decode image.")
            val scale = minOf(1.0, 1800.0 / max(original.width, original.height))
            val w = max(1, (original.width * scale).toInt())
            val h = max(1, (original.height * scale).toInt())
            sourceBitmap = if (scale < 1.0) Bitmap.createScaledBitmap(original, w, h, true) else original
            resultBitmap = null
            preview.setImageBitmap(sourceBitmap)
            maskView.setSourceSize(w, h)
            maskView.isEnabled = true
            removeButton.isEnabled = true
            status.text = "Image ready ($w × $h). Paint the watermark area."
        } catch (e: Exception) {
            status.text = "Could not open image: ${e.message ?: "unknown error"}"
        }
    }

    private fun removeWatermark() {
        val src = sourceBitmap ?: return toast("Choose an image first.")
        if (!openCvReady) return toast("Image engine is not available.")
        try {
            val source = Mat()
            Utils.bitmapToMat(src.copy(Bitmap.Config.ARGB_8888, true), source)
            val bgr = Mat()
            Imgproc.cvtColor(source, bgr, Imgproc.COLOR_RGBA2RGB)
            val maskBitmap = maskView.exportMask(src.width, src.height)
            val maskMat = Mat()
            Utils.bitmapToMat(maskBitmap, maskMat)
            Imgproc.cvtColor(maskMat, maskMat, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.threshold(maskMat, maskMat, 1.0, 255.0, Imgproc.THRESH_BINARY)
            val output = Mat()
            Photo.inpaint(bgr, maskMat, output, 3.0, Photo.INPAINT_TELEA)
            val rgba = Mat()
            Imgproc.cvtColor(output, rgba, Imgproc.COLOR_RGB2RGBA)
            val bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(rgba, bitmap)
            resultBitmap = bitmap
            preview.setImageBitmap(bitmap)
            status.text = "Finished. Check edges carefully, then Save Result."
            source.release(); bgr.release(); maskMat.release(); output.release(); rgba.release()
        } catch (e: Exception) {
            status.text = "Removal failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun saveResult() {
        val bitmap = resultBitmap ?: return toast("Remove a watermark first.")
        try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "My_Watermark_Removed_${System.currentTimeMillis()}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MyWatermarkRemover")
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create output file.")
            contentResolver.openOutputStream(uri).use { out ->
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Could not save image.")
                }
            }
            status.text = "Saved to Pictures/MyWatermarkRemover."
            toast("Image saved.")
        } catch (e: Exception) {
            status.text = "Save failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class MaskDrawingView(context: android.content.Context) : View(context) {
        private val maskPath = Path()
        private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(145, 0, 190, 170)
            style = Paint.Style.STROKE
            strokeWidth = dp(22).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private var imageW = 1
        private var imageH = 1

        fun setSourceSize(w: Int, h: Int) { imageW = w; imageH = h; clearMask() }
        fun clearMask() { maskPath.reset(); invalidate() }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawPath(maskPath, overlayPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { maskPath.moveTo(event.x, event.y); invalidate(); return true }
                MotionEvent.ACTION_MOVE -> { maskPath.lineTo(event.x, event.y); invalidate(); return true }
                MotionEvent.ACTION_UP -> { maskPath.lineTo(event.x, event.y); invalidate(); return true }
            }
            return true
        }

        fun exportMask(width: Int, height: Int): Bitmap {
            val bm = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            val sx = width / this.width.coerceAtLeast(1).toFloat()
            val sy = height / this.height.coerceAtLeast(1).toFloat()
            val matrix = android.graphics.Matrix().apply { setScale(sx, sy) }
            val mappedPath = Path(maskPath).apply { transform(matrix) }
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = dp(22) * ((sx + sy) / 2f)
            }
            canvas.drawPath(mappedPath, p)
            return bm
        }
    }
}
