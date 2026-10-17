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

    private var selectedVideoUri: Uri? = null
    private lateinit var videoStatus: TextView
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) loadImage(uri)
    }
    private val pickVideo = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedVideoUri = uri
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Video selected"
            videoStatus.text = "Selected: $name\nVideo watermark removal is not available in this build yet."
        }
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

        val videoScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
        }
        val videoContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(20))
        }
        val videoHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(4, 78, 65), Color.rgb(0, 151, 112))).apply {
                cornerRadius = dp(24).toFloat()
            }
        }
        videoHeader.addView(TextView(this).apply {
            text = "VIDEO STUDIO  •  PALIAAPK HUB"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(Color.rgb(194, 241, 223))
        })
        videoHeader.addView(TextView(this).apply {
            text = "Video watermark\nremoval"
            textSize = 28f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(18), 0, dp(7))
        })
        videoHeader.addView(TextView(this).apply {
            text = "Choose a clip to prepare your video workspace."
            textSize = 14f
            setTextColor(Color.rgb(221, 244, 235))
        })
        videoContent.addView(videoHeader, margins(bottom = 16))
        val videoCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(18), dp(26), dp(18), dp(22))
            background = rounded(Color.WHITE, 22, Color.rgb(229, 238, 233), dp(1))
        }
        videoCard.addView(TextView(this).apply {
            text = "▶"
            textSize = 48f
            gravity = Gravity.CENTER
            setTextColor(emerald)
        })
        videoCard.addView(TextView(this).apply {
            text = "Select a video"
            textSize = 20f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ink)
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(6))
        })
        videoCard.addView(TextView(this).apply {
            text = "MP4, MOV and other videos supported by your phone's gallery."
            textSize = 13f
            setTextColor(muted)
            gravity = Gravity.CENTER
        })
        videoCard.addView(makeButton("▶   Choose video", true).apply {
            setOnClickListener { pickVideo.launch("video/*") }
        }, margins(top = 18))
        videoStatus = TextView(this).apply {
            text = "Video watermark removal engine is not included yet. This option currently lets you select a video only."
            textSize = 13f
            setTextColor(emerald)
            setPadding(dp(13), dp(12), dp(13), dp(12))
            background = rounded(Color.rgb(231, 247, 240), 14)
        }
        videoCard.addView(videoStatus, margins(top = 14))
        videoContent.addView(videoCard)
        videoContent.addView(TextView(this).apply {
            text = "Video cleanup is a separate feature and needs a video-processing engine. This screen does not claim to remove or export a cleaned video yet."
            textSize = 12f
            setTextColor(muted)
            setPadding(dp(4), dp(16), dp(4), dp(8))
        })
        videoScroll.addView(videoContent)

        val homeScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
        }
        val homeContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val homeHero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(18, 59, 82), Color.rgb(23, 106, 103))).apply { cornerRadius = dp(28).toFloat() }
            elevation = dp(4).toFloat()
        }
        val heroBrand = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        heroBrand.addView(TextView(this).apply {
            text = "✦"
            textSize = 23f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(18, 201, 160), 15)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brandCopy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brandCopy.addView(TextView(this).apply {
            text = "MY WATERMARK REMOVER"
            textSize = 12f
            letterSpacing = 0.07f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        brandCopy.addView(TextView(this).apply {
            text = "Branding by PaliaAPK HUB"
            textSize = 11f
            setTextColor(Color.rgb(191, 225, 220))
            setPadding(0, dp(3), 0, 0)
        })
        heroBrand.addView(brandCopy, LinearLayout.LayoutParams(0, -2, 1f))
        heroBrand.addView(TextView(this).apply {
            text = if (openCvReady) "● READY" else "● CHECK"
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(7), dp(10), dp(7))
            background = rounded(Color.rgb(39, 119, 111), 30)
        })
        homeHero.addView(heroBrand)
        homeHero.addView(TextView(this).apply {
            text = "Clean photos.\nKeep the moment."
            textSize = 30f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setLineSpacing(dp(1).toFloat(), 1f)
            setPadding(0, dp(22), 0, dp(8))
        })
        homeHero.addView(TextView(this).apply {
            text = "Remove unwanted marks from images — right on your phone."
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(Color.rgb(220, 240, 235))
        })
        homeHero.addView(TextView(this).apply {
            text = "OFFLINE IMAGE CLEANUP  ·  DEVELOPER BY SHANPALIA"
            textSize = 9f
            letterSpacing = 0.025f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(166, 224, 211))
            setPadding(0, dp(18), 0, 0)
        })
        homeContent.addView(homeHero, margins(bottom = 22))
        homeContent.addView(TextView(this).apply {
            text = "Welcome to your studio"
            textSize = 21f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(ink)
        }, margins(bottom = 4))
        homeContent.addView(TextView(this).apply {
            text = "Choose a tool to get started"
            textSize = 13f
            setTextColor(muted)
        }, margins(bottom = 13))
        var navigateToPage: ((Int) -> Unit)? = null
        fun workspaceCard(icon: String, title: String, subtitle: String, page: Int, tint: Int): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(15), dp(15), dp(14), dp(15))
                background = rounded(Color.WHITE, 22, Color.rgb(226, 236, 232), dp(1))
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
            }
            card.addView(TextView(this).apply {
                text = icon
                textSize = 25f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(tint)
                background = rounded(Color.rgb(230, 248, 242), 17)
            }, LinearLayout.LayoutParams(dp(58), dp(60)))
            val info = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), 0, dp(5), 0)
            }
            info.addView(TextView(this).apply {
                text = title
                textSize = 17f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ink)
            })
            info.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setLineSpacing(dp(2).toFloat(), 1f)
                setTextColor(muted)
                setPadding(0, dp(4), 0, 0)
            })
            card.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            card.addView(TextView(this).apply {
                text = "›"
                textSize = 30f
                setTextColor(emerald)
            })
            card.setOnClickListener { navigateToPage?.invoke(page) }
            return card
        }
        homeContent.addView(workspaceCard("▧", "Photo cleanup", "Brush over a watermark and clean your image offline", 1, Color.rgb(0, 137, 100)), margins(bottom = 11))
        homeContent.addView(workspaceCard("▶", "Video studio", "Open a video workspace and choose a clip", 2, Color.rgb(18, 106, 103)), margins(bottom = 11))
        homeContent.addView(workspaceCard("⚙", "App settings", "Processing status, privacy and app information", 3, Color.rgb(18, 59, 82)), margins(bottom = 18))
        val homeStatus = TextView(this).apply {
            text = if (openCvReady) "✓  OFFLINE ENGINE READY" else "⚠  IMAGE ENGINE NEEDS ATTENTION"
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(17, 116, 91))
            setPadding(dp(15), dp(15), dp(15), dp(15))
            background = rounded(Color.rgb(226, 248, 240), 17)
        }
        homeContent.addView(homeStatus, margins(bottom = 10))
        homeContent.addView(TextView(this).apply {
            text = "Your photos stay on this device. Online AI and video cleanup will need their own processing engines."
            textSize = 12f
            setLineSpacing(dp(3).toFloat(), 1f)
            setTextColor(muted)
            setPadding(dp(3), dp(2), dp(3), dp(8))
        })
        homeScroll.addView(homeContent)

        val settingsScroll = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            visibility = View.GONE
        }
        val settingsContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(20))
        }
        val settingsHero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(3, 73, 62), Color.rgb(0, 151, 112))).apply { cornerRadius = dp(24).toFloat() }
        }
        settingsHero.addView(TextView(this).apply {
            text = "APP PREFERENCES"
            textSize = 11f
            letterSpacing = 0.1f
            setTextColor(Color.rgb(194, 241, 223))
        })
        settingsHero.addView(TextView(this).apply {
            text = "Settings"
            textSize = 29f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(12), 0, dp(4))
        })
        settingsHero.addView(TextView(this).apply {
            text = "My Watermark Remover by PaliaAPK HUB"
            textSize = 13f
            setTextColor(Color.rgb(221, 244, 235))
        })
        settingsContent.addView(settingsHero, margins(bottom = 16))
        fun settingCard(title: String, subtitle: String): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(16))
                background = rounded(Color.WHITE, 18, Color.rgb(225, 237, 231), dp(1))
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(ink)
            })
            card.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(muted)
                setPadding(0, dp(5), 0, 0)
            })
            return card
        }
        settingsContent.addView(settingCard("Offline mode", "Image cleanup runs on your phone using OpenCV. No upload is needed."), margins(bottom = 10))
        settingsContent.addView(settingCard("Online mode", "Online AI removal is not configured yet. A secure backend/API is required before this can work."), margins(bottom = 10))
        settingsContent.addView(settingCard("Video processing", "Video selection is available; watermark removal and export need a video-processing engine."), margins(bottom = 10))
        settingsContent.addView(settingCard("App information", "My Watermark Remover\nBy PaliaAPK HUB\nDeveloper By shanpalia\nVersion 1.0"), margins(bottom = 10))
        settingsContent.addView(makeButton("Open Photo workspace", true).apply {
            setOnClickListener { navigateToPage?.invoke(1) }
        }, margins(top = 4, bottom = 8))
        settingsContent.addView(makeButton("Open Video workspace", false).apply {
            setOnClickListener { navigateToPage?.invoke(2) }
        })
        settingsScroll.addView(settingsContent)

        root.addView(homeScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(0) })
        root.addView(videoScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(settingsScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(6), dp(5), dp(6))
            background = rounded(Color.WHITE, 20, Color.rgb(229, 238, 233), dp(1))
            elevation = dp(5).toFloat()
        }
        lateinit var homeTab: TextView
        lateinit var imageTab: TextView
        lateinit var videoTab: TextView
        lateinit var settingsTab: TextView
        fun navItem(icon: String, label: String, action: () -> Unit): TextView {
            return TextView(this).apply {
                text = "$icon\n$label"
                textSize = 10f
                gravity = Gravity.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setTextColor(muted)
                setPadding(dp(3), dp(5), dp(3), dp(5))
                background = rounded(Color.WHITE, 14)
                setOnClickListener { action() }
            }
        }
        fun showPage(page: Int) {
            homeScroll.visibility = if (page == 0) View.VISIBLE else View.GONE
            scroll.visibility = if (page == 1) View.VISIBLE else View.GONE
            videoScroll.visibility = if (page == 2) View.VISIBLE else View.GONE
            settingsScroll.visibility = if (page == 3) View.VISIBLE else View.GONE
            val tabs = listOf(homeTab, imageTab, videoTab, settingsTab)
            tabs.forEachIndexed { index, tab ->
                val active = index == page
                tab.setTextColor(if (active) emerald else muted)
                tab.background = if (active) rounded(Color.rgb(231, 247, 240), 14) else rounded(Color.WHITE, 14)
            }
        }
        navigateToPage = { page -> showPage(page) }
        homeTab = navItem("⌂", "Home") { showPage(0) }
        imageTab = navItem("▧", "Image") { showPage(1) }
        videoTab = navItem("▶", "Video") { showPage(2) }
        settingsTab = navItem("⚙", "Settings") { showPage(3) }
        nav.addView(homeTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(imageTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(videoTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        nav.addView(settingsTab, LinearLayout.LayoutParams(0, dp(54), 1f))
        root.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        showPage(0)
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
