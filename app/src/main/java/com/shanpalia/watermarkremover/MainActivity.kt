package com.shanpalia.watermarkremover

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
    private lateinit var chooseButton: Button
    private lateinit var emptyState: View
    private var openCvReady = false
    private val emerald = Color.rgb(0, 137, 100)
    private val ink = Color.rgb(24, 43, 39)
    private val muted = Color.rgb(100, 117, 111)
    private val mint = Color.rgb(235, 250, 245)

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.WHITE
        window.navigationBarColor = Color.WHITE
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        openCvReady = try {
            OpenCVLoader.initLocal()
        } catch (e: Throwable) {
            android.util.Log.e("MyWatermarkRemover", "OpenCV initialization failed", e)
            false
        }
        buildUi()
        status.text = if (openCvReady) "Ready when you are — choose a photo to begin."
        else "Image engine couldn't start. Try reinstalling the latest build."
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(247, 250, 248))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(dp(18), bars.top + dp(10), dp(18), bars.bottom + dp(10))
            insets
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            isVerticalScrollBarEnabled = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(15), dp(18), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(4, 78, 65), Color.rgb(0, 151, 112))).apply { cornerRadius = dp(24).toFloat() }
        }
        val brandLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        brandLine.addView(TextView(this).apply {
            text = "✦"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(31, 143, 111), 14)
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        brandLine.addView(TextView(this).apply {
            text = "PALIAAPK HUB"
            textSize = 12f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(194, 241, 223))
            setPadding(dp(11), 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, dp(44), 1f))
        brandLine.addView(TextView(this).apply {
            text = "● OFFLINE"
            textSize = 10f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(Color.rgb(28, 126, 97), 30)
        })
        header.addView(brandLine)
        header.addView(TextView(this).apply {
            text = "Make watermarks\ndisappear."
            textSize = 25f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(13), 0, dp(5))
        })
        header.addView(TextView(this).apply {
            text = "Mark the area. Clean it up. Save your photo."
            textSize = 14f
            setTextColor(Color.rgb(221, 244, 235))
            setLineSpacing(dp(2).toFloat(), 1f)
        })
        content.addView(header, margins(bottom = 14))

        val titleLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleLine.addView(TextView(this).apply {
            text = "Your canvas"
            textSize = 19f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ink)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        titleLine.addView(TextView(this).apply {
            text = "01  SELECT PHOTO"
            textSize = 10f
            letterSpacing = 0.08f
            setTextColor(emerald)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(Color.rgb(220, 245, 236), 30)
        })
        content.addView(titleLine, margins(bottom = 10))

        val workCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(14))
            background = rounded(Color.WHITE, 22, Color.rgb(229, 238, 233), dp(1))
            elevation = dp(2).toFloat()
        }
        val frame = FrameLayout(this).apply {
            background = rounded(mint, 16)
            clipToOutline = true
        }
        preview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Selected image preview"
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        maskView = MaskDrawingView(this).apply { isEnabled = false }
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        frame.addView(maskView, FrameLayout.LayoutParams(-1, -1))
        emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(TextView(this@MainActivity).apply {
                text = "▧"
                textSize = 42f
                gravity = Gravity.CENTER
                setTextColor(emerald)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Ready for your photo"
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(ink)
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@MainActivity).apply {
                text = "Tap below to open your gallery"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(muted)
                setPadding(0, dp(5), 0, 0)
            })
        }
        frame.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        workCard.addView(frame, LinearLayout.LayoutParams(-1, dp(205)))
        chooseButton = makeButton("＋   Choose image", true).apply {
            setOnClickListener { pickImage.launch("image/*") }
        }
        workCard.addView(chooseButton, margins(top = 12))
        content.addView(workCard)

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        buttons.addView(makeButton("Reset", false).apply {
            setOnClickListener { maskView.clearMask(); status.text = "Brush marks cleared." }
        }, LinearLayout.LayoutParams(0, dp(50), 1f).apply { marginEnd = dp(8) })
        removeButton = makeButton("Remove watermark", true).apply {
            isEnabled = false
            setOnClickListener { removeWatermark() }
        }
        buttons.addView(removeButton, LinearLayout.LayoutParams(0, dp(50), 1.35f))
        content.addView(buttons, margins(top = 12))
        content.addView(makeButton("↓   Save cleaned photo", false).apply {
            setOnClickListener { saveResult() }
        }, margins(top = 9))

        content.addView(TextView(this).apply {
            text = "HOW TO USE"
            textSize = 11f
            letterSpacing = 0.1f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(muted)
            setPadding(dp(2), dp(20), 0, dp(9))
        })
        val steps = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = rounded(Color.WHITE, 18, Color.rgb(230, 239, 234), dp(1))
        }
        steps.addView(TextView(this).apply {
            text = "① Select photo     ② Brush area     ③ Save result"
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ink)
        })
        steps.addView(TextView(this).apply {
            text = "Use your finger to mark only the area you want to clean."
            textSize = 12f
            setTextColor(muted)
            setPadding(0, dp(5), 0, 0)
        })
        content.addView(steps)

        status = TextView(this).apply {
            textSize = 13f
            setTextColor(emerald)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(Color.rgb(231, 247, 240), 14)
        }
        content.addView(status, margins(top = 13))
        content.addView(TextView(this).apply {
            text = "MADE BY PALIAAPK HUB  ·  DEVELOPER SHANPALIA\nOffline image cleanup powered by OpenCV. Results depend on the marked area; edit only images you have permission to modify."
            textSize = 11f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(muted)
            setPadding(dp(3), dp(15), dp(3), dp(8))
        })
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun makeButton(label: String, primary: Boolean): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(48)
        minimumHeight = dp(48)
        setPadding(dp(12), dp(5), dp(12), dp(5))
        background = rounded(if (primary) emerald else Color.WHITE, 15,
            if (primary) emerald else Color.rgb(219, 231, 225), dp(1))
        setTextColor(if (primary) Color.WHITE else emerald)
        stateListAnimator = null
        if (Build.VERSION.SDK_INT >= 21) elevation = if (primary) dp(2).toFloat() else 0f
    }

    private fun rounded(color: Int, radius: Int, strokeColor: Int? = null, strokeWidth: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (strokeColor != null && strokeWidth > 0) setStroke(strokeWidth, strokeColor)
        }

    private fun margins(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(-1, -2).apply {
            if (top > 0) topMargin = dp(top)
            if (bottom > 0) bottomMargin = dp(bottom)
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
            emptyState.visibility = View.GONE
            maskView.setSourceSize(w, h)
            maskView.isEnabled = true
            removeButton.isEnabled = true
            chooseButton.text = "↻   Change photo"
            status.text = "Photo loaded (${w} × ${h}). Brush over the watermark."
        } catch (e: Exception) {
            status.text = "Couldn't open image: ${e.message ?: "unknown error"}"
        }
    }

    private fun removeWatermark() {
        val src = sourceBitmap ?: return toast("Choose an image first.")
        if (!openCvReady) return toast("Image engine is not available.")
        removeButton.isEnabled = false
        status.text = "Cleaning the selected area…"
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
            status.text = "Cleanup finished. Review the photo, then save it."
            source.release()
            bgr.release()
            maskMat.release()
            output.release()
            rgba.release()
        } catch (e: Exception) {
            android.util.Log.e("MyWatermarkRemover", "Removal failed", e)
            status.text = "Cleanup failed: ${e.message ?: "unknown error"}"
        } finally {
            removeButton.isEnabled = sourceBitmap != null
        }
    }

    private fun saveResult() {
        val bitmap = resultBitmap ?: return toast("Clean a photo first.")
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
            toast("Photo saved to Pictures.")
        } catch (e: Exception) {
            status.text = "Save failed: ${e.message ?: "unknown error"}"
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private inner class MaskDrawingView(context: android.content.Context) : View(context) {
        private val maskPath = Path()
        private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(175, 0, 150, 110)
            style = Paint.Style.STROKE
            strokeWidth = dp(22).toFloat()
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        fun setSourceSize(w: Int, h: Int) { clearMask() }
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
            val mappedPath = Path(maskPath).apply {
                transform(android.graphics.Matrix().apply { setScale(sx, sy) })
            }
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
