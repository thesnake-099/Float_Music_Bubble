package com.meni.bubble_app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.meni.bubble_app.databinding.LayoutCloseTargetBinding
import com.meni.bubble_app.databinding.LayoutFloatingBubbleBinding
import com.meni.bubble_app.databinding.LayoutFloatingWindowBinding
import com.meni.bubble_app.databinding.LayoutMiniBubbleBinding
import com.meni.bubble_app.databinding.LayoutMiniPlayerBinding
import kotlin.math.abs

class FloatingService : Service() {

    companion object {
        var IS_SERVICE_RUNNING = false
        fun isAnyYoutubeWindowOpen(): Boolean {
            return instance?.activeWindows?.values?.any { it.currentUrl.contains("youtube.com") }
                ?: false
        }

        private const val NOTIFICATION_CHANNEL_ID = "FloatingServiceChannel"
        private const val PREFS_NAME = "FloatingBubblePrefs"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val TAG = "FloatingService"
        private const val YOUTUBE_HOME_URL = "https://m.youtube.com"
        private var instance: FloatingService? = null
    }

    private data class FloatingWindowInstance(
        val id: String,
        var windowBinding: LayoutFloatingWindowBinding,
        var windowLayoutParams: WindowManager.LayoutParams,
        var currentUrl: String,
        var windowTitle: String,
        var currentVideoId: String? = null,
        var miniBubbleBinding: LayoutMiniBubbleBinding? = null,
        var miniBubbleLayoutParams: WindowManager.LayoutParams? = null,
        var miniPlayerBinding: LayoutMiniPlayerBinding? = null,
        var miniPlayerLayoutParams: WindowManager.LayoutParams? = null,
        var customView: View? = null,
        var myChromeClient: MyChromeClient? = null
    )

    private val activeWindows = mutableMapOf<String, FloatingWindowInstance>()
    private lateinit var windowManager: WindowManager
    private lateinit var sharedPreferences: SharedPreferences
    private var bubbleBinding: LayoutFloatingBubbleBinding? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var closeTargetBinding: LayoutCloseTargetBinding? = null
    private var closeTargetLayoutParams: WindowManager.LayoutParams? = null
    private var closeTargetRect: Rect? = null
    private var lastTouchRawX: Float = 0f
    private var lastTouchRawY: Float = 0f


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForeground(1, createNotification())
        IS_SERVICE_RUNNING = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val url = intent?.getStringExtra("url")
        val title = intent?.getStringExtra("title") ?: "Smart Bubble"
        if (url != null) {
            val isPremium = SubscriptionManager.isSubscribed.value == true
            if (!isPremium && activeWindows.isNotEmpty()) {
                val existingInstanceId = activeWindows.keys.first()
                closeWindow(existingInstanceId)
            }
            if (isPremium) {
                showNewOrExistingWindow(url, title)
            } else {
                showOrUpdateSingleWindow(url, title)
            }
        }
        val shouldShowPanel = intent?.getBooleanExtra("SHOW_PANEL", false) ?: false
        if (shouldShowPanel) {
            togglePanel(true)
        }

        return START_NOT_STICKY
    }

    private fun showOrUpdateSingleWindow(url: String, title: String) {
        if (activeWindows.isEmpty()) {
            createAndShowWindow("single_window", url, title)
        } else {
            val instance = activeWindows.values.first()
            instance.windowBinding.buttonBack.visibility = View.GONE
            instance.currentUrl = url
            instance.windowTitle = title
            instance.windowBinding.windowTitle.text = title
            instance.windowBinding.webView.loadUrl(url)
            instance.windowBinding.root.visibility = View.VISIBLE
            instance.windowBinding.controlsContainer.visibility = View.VISIBLE
            bringWindowToFront(instance)
        }
    }

    private fun showNewOrExistingWindow(url: String, title: String) {
        val windowId = url
        if (activeWindows.containsKey(windowId)) {
            val instance = activeWindows[windowId]!!
            bringWindowToFront(instance)
        } else {
            createAndShowWindow(windowId, url, title)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun createAndShowWindow(id: String, url: String, title: String) {
        togglePanel(false)
        val windowBinding = LayoutFloatingWindowBinding.inflate(LayoutInflater.from(this))
        val layoutFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        val windowLayoutParams = WindowManager.LayoutParams(
            (350 * resources.displayMetrics.density).toInt(),
            (400 * resources.displayMetrics.density).toInt(),
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }
        val newInstance = FloatingWindowInstance(
            id = id,
            windowBinding = windowBinding,
            windowLayoutParams = windowLayoutParams,
            currentUrl = url,
            windowTitle = title
        )
        val myChromeClient = MyChromeClient(newInstance)
        newInstance.myChromeClient = myChromeClient
        windowBinding.webView.apply {
            webViewClient = MyWebViewClient(newInstance)
            webChromeClient = myChromeClient
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.databaseEnabled = true; settings.mediaPlaybackRequiresUserGesture = false
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 10; SM-G975F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/86.0.4240.198 Mobile Safari/537.36"
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true); cookieManager.setAcceptThirdPartyCookies(
            this,
            true
        )
            addJavascriptInterface(WebAppInterface(newInstance), "AndroidInterface")
        }
        val windowRoot = windowBinding.root
        windowRoot.alpha = 0f
        windowRoot.scaleX = 0.9f
        windowRoot.scaleY = 0.9f
        windowManager.addView(windowRoot, windowLayoutParams)
        windowRoot.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .start()
        setupWindowTouchListeners(newInstance)
        setupWindowControls(newInstance)
        newInstance.currentVideoId = null
        windowBinding.buttonBack.visibility = View.GONE
        windowBinding.webView.loadUrl(url)
        windowBinding.windowTitle.text = title
        activeWindows[id] = newInstance
    }

    private fun setupWindowControls(instance: FloatingWindowInstance) {
        val binding = instance.windowBinding
        binding.buttonClose.setOnClickListener {
            closeWindow(instance.id)
        }
        binding.buttonMinimize.setOnClickListener {
            if (instance.currentVideoId != null) {
                switchToMiniPlayer(instance)
            } else {
                switchToMiniBubble(instance)
            }
        }
        binding.buttonBack.setOnClickListener {
            if (instance.currentVideoId != null) {
                binding.webView.loadUrl(YOUTUBE_HOME_URL)
                instance.currentVideoId = null
                binding.buttonBack.visibility = View.GONE
            } else if (instance.customView != null) {
                instance.myChromeClient?.exitFullScreen()
            } else if (binding.webView.canGoBack()) {
                binding.webView.goBack()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWindowTouchListeners(instance: FloatingWindowInstance) {
        val windowBinding = instance.windowBinding
        val windowLayoutParams = instance.windowLayoutParams
        val originalColor = ContextCompat.getColor(this, R.color.cardStroke)
        val pressedColor = ContextCompat.getColor(this, R.color.titlebackground)
        val moveListener = object : View.OnTouchListener {
            private var initialX = 0;
            private var initialY = 0
            private var initialTouchX = 0f;
            private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowLayoutParams.x; initialY = windowLayoutParams.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY;
                        // IMPROVEMENT 5: Change color on press.
                        (v as? android.widget.ImageView)?.setColorFilter(pressedColor)
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        windowLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        windowLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(windowBinding.root, windowLayoutParams)
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // IMPROVEMENT 5: Revert color on release.
                        (v as? android.widget.ImageView)?.setColorFilter(originalColor)
                        return true
                    }
                }
                return false
            }
        }

        windowBinding.moveHandle.setOnTouchListener(moveListener)
        windowBinding.titleBar.setOnTouchListener(moveListener)
        windowBinding.resizeHandleBottomRight.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0; private var initialHeight = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = windowLayoutParams.width; initialHeight = windowLayoutParams.height
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        (v as? android.widget.ImageView)?.setColorFilter(pressedColor)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newWidth = initialWidth + (event.rawX - initialTouchX).toInt()
                        val newHeight = initialHeight + (event.rawY - initialTouchY).toInt()
                        if (newWidth > (200 * resources.displayMetrics.density) && newHeight > (200 * resources.displayMetrics.density)) {
                            windowLayoutParams.width = newWidth
                            windowLayoutParams.height = newHeight
                            windowManager.updateViewLayout(windowBinding.root, windowLayoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v as? android.widget.ImageView)?.setColorFilter(originalColor)
                        return true
                    }
                }
                return false
            }
        })

        windowBinding.resizeHandleBottomLeft.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0; private var initialHeight = 0
            private var initialX = 0; private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = windowLayoutParams.width; initialHeight = windowLayoutParams.height
                        initialX = windowLayoutParams.x; initialTouchX = event.rawX; initialTouchY = event.rawY
                        (v as? android.widget.ImageView)?.setColorFilter(pressedColor)
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val newWidth = initialWidth - dx
                        val newHeight = initialHeight + (event.rawY - initialTouchY).toInt()
                        if (newWidth > (200 * resources.displayMetrics.density) && newHeight > (200 * resources.displayMetrics.density)) {
                            windowLayoutParams.x = initialX + dx
                            windowLayoutParams.width = newWidth
                            windowLayoutParams.height = newHeight
                            windowManager.updateViewLayout(windowBinding.root, windowLayoutParams)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        (v as? android.widget.ImageView)?.setColorFilter(originalColor)
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun switchToMainWindow(instance: FloatingWindowInstance) {
        val viewToMove = instance.customView ?: instance.windowBinding.webView
        val webView = viewToMove as? WebView ?: return
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.onPause()
        (viewToMove.parent as? ViewGroup)?.removeView(viewToMove)
        instance.windowBinding.contentFrame.addView(viewToMove)
        webView.onResume()
        webView.evaluateJavascript("document.querySelector('video').play();") {
            webView.setLayerType(View.LAYER_TYPE_NONE, null)
        }
        instance.miniPlayerBinding?.root?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        instance.miniPlayerBinding = null
        instance.windowBinding.root.visibility = View.VISIBLE
        instance.windowBinding.controlsContainer.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun switchToMiniPlayer(instance: FloatingWindowInstance) {
        val viewToMove = instance.customView ?: instance.windowBinding.webView
        val webView = viewToMove as? WebView ?: return
        instance.windowBinding.root.visibility = View.GONE
        instance.windowBinding.controlsContainer.visibility = View.GONE
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        webView.onPause()

        if (instance.miniPlayerBinding == null) {
            val miniPlayerBinding = LayoutMiniPlayerBinding.inflate(LayoutInflater.from(this))
            val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val miniPlayerLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.END; x = 16; y = 16 }
            windowManager.addView(miniPlayerBinding.root, miniPlayerLayoutParams)
            instance.miniPlayerBinding = miniPlayerBinding
            instance.miniPlayerLayoutParams = miniPlayerLayoutParams
        }
        (viewToMove.parent as? ViewGroup)?.removeView(viewToMove)
        instance.miniPlayerBinding?.videoContainer?.addView(viewToMove)
        webView.onResume()
        webView.evaluateJavascript("document.querySelector('video').play();") {
            webView.setLayerType(View.LAYER_TYPE_NONE, null)
        }

        instance.miniPlayerBinding?.closeMiniPlayerButton?.setOnClickListener {
            switchToMainWindow(
                instance
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun switchToMiniBubble(instance: FloatingWindowInstance) {
        instance.windowBinding.root.visibility = View.GONE
        instance.windowBinding.controlsContainer.visibility = View.GONE
        if (instance.miniBubbleBinding == null) {
            val miniBubbleBinding = LayoutMiniBubbleBinding.inflate(LayoutInflater.from(this))
            val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            val miniBubbleLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                val displayMetrics = resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val screenHeight = displayMetrics.heightPixels
                x = screenWidth - 150
                y = (screenHeight / 2) - 75
            }
            windowManager.addView(miniBubbleBinding.root, miniBubbleLayoutParams)
            instance.miniBubbleBinding = miniBubbleBinding
            instance.miniBubbleLayoutParams = miniBubbleLayoutParams
            setupMiniBubbleTouchListener(instance)
        }
        val iconResId = when {
            instance.currentUrl.contains("youtube.com") -> R.drawable.ic_youtube
            instance.currentUrl.contains("chat.openai.com") -> R.drawable.ic_chatgpt
            instance.currentUrl.contains("translate.google.com") -> R.drawable.ic_googletrans
            else -> R.drawable.ic_web
        }
        instance.miniBubbleBinding?.miniBubbleIcon?.setImageResource(iconResId)
    }

    private fun switchToMainWindowFromBubble(instance: FloatingWindowInstance) {
        instance.miniBubbleBinding?.root?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        instance.miniBubbleBinding = null
        instance.windowBinding.root.visibility = View.VISIBLE
        instance.windowBinding.controlsContainer.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniBubbleTouchListener(instance: FloatingWindowInstance) {
        val miniBubbleBinding = instance.miniBubbleBinding ?: return
        val miniBubbleLayoutParams = instance.miniBubbleLayoutParams ?: return
        miniBubbleBinding.root.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0;
            private var initialY = 0
            private var initialTouchX = 0f;
            private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = miniBubbleLayoutParams.x; initialY = miniBubbleLayoutParams.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY
                        lastTouchRawX = event.rawX; lastTouchRawY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        lastTouchRawX = event.rawX; lastTouchRawY = event.rawY
                        miniBubbleLayoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        miniBubbleLayoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(
                            miniBubbleBinding.root,
                            miniBubbleLayoutParams
                        )
                        showCloseTarget()
                        val isOver =
                            closeTargetRect?.contains(lastTouchRawX.toInt(), lastTouchRawY.toInt())
                                ?: false
                        updateCloseTargetVisual(isOver)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasOver =
                            closeTargetRect?.contains(event.rawX.toInt(), event.rawY.toInt())
                                ?: false
                        hideCloseTarget()
                        if (wasOver) {
                            performHapticFeedback()
                            closeWindow(instance.id)
                        } else if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            switchToMainWindowFromBubble(instance)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun bringWindowToFront(instance: FloatingWindowInstance) {
        try {
            val view = instance.windowBinding.root
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
                windowManager.addView(view, instance.windowLayoutParams)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bring window to front: ${e.message}")
        }
    }

    private fun closeWindow(id: String) {
        val instance = activeWindows[id] ?: return
        Log.d(TAG, "Closing window with id: $id")

        try {
            windowManager.removeView(instance.windowBinding.root)
        } catch (e: Exception) {
        }
        try {
            instance.miniBubbleBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            instance.miniPlayerBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        activeWindows.remove(id)
    }

    private fun showCloseTarget() {
        if (closeTargetBinding == null) {
            closeTargetBinding = LayoutCloseTargetBinding.inflate(LayoutInflater.from(this))
            val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            closeTargetLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 250 }
        }
        try {
            val root = closeTargetBinding?.root
            if (root != null && !root.isAttachedToWindow) {
                windowManager.addView(root, closeTargetLayoutParams)
                root.alpha = 0.7f; root.scaleX = 1.0f; root.scaleY = 1.0f
            }
            root?.post {
                try {
                    val loc = IntArray(2); root.getLocationOnScreen(loc)
                    val w = root.width;
                    val h = root.height
                    closeTargetRect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
                    val over =
                        closeTargetRect?.contains(lastTouchRawX.toInt(), lastTouchRawY.toInt())
                            ?: false
                    updateCloseTargetVisual(over)
                } catch (e: Exception) {
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun hideCloseTarget() {
        try {
            closeTargetBinding?.root?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        } catch (e: Exception) {
        } finally {
            closeTargetBinding = null; closeTargetRect = null
        }
    }

    private fun updateCloseTargetVisual(isOver: Boolean) {
        closeTargetBinding?.root?.let { root ->
            root.alpha = if (isOver) 1.0f else 0.7f
            val scale = if (isOver) 1.2f else 1.0f
            root.scaleX = scale; root.scaleY = scale
        }
    }

    private fun performHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        50,
                        VibrationEffect.EFFECT_HEAVY_CLICK
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun createBubble() {
        bubbleBinding = LayoutFloatingBubbleBinding.inflate(LayoutInflater.from(this))
        val layoutFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
        bubbleLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0; y = sharedPreferences.getInt(KEY_BUBBLE_Y, 100)
        }
        windowManager.addView(bubbleBinding?.root, bubbleLayoutParams);
        setupBubbleTouchListener()
        setupPanelClickListeners()
        if (CustomUrlManager.hasCustomUrl()) {
            bubbleBinding?.iconCustomUrl?.visibility = View.VISIBLE
        } else {
            bubbleBinding?.iconCustomUrl?.visibility = View.GONE
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubbleTouchListener() {
        val touchListener = object : View.OnTouchListener {
            private var initialY = 0;
            private var initialTouchX = 0f;
            private var initialTouchY = 0f
            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialY = bubbleLayoutParams!!.y; initialTouchX =
                            event.rawX; initialTouchY = event.rawY; return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        if (abs(dx) > 50 && abs(event.rawY - initialTouchY) < 50) {
                            togglePanel(true)
                        }
                        bubbleLayoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleBinding?.root, bubbleLayoutParams)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        sharedPreferences.edit().putInt(KEY_BUBBLE_Y, bubbleLayoutParams!!.y)
                            .apply()
                        if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            togglePanel(true)
                        }
                        return true
                    }
                }
                return false
            }
        }
        bubbleBinding?.collapsedHandle?.setOnTouchListener(touchListener); bubbleBinding?.expandedPanel?.setOnTouchListener(
            touchListener
        ); bubbleBinding?.collapseHandle?.setOnClickListener { togglePanel(false) }
    }

    private fun setupPanelClickListeners() {
        bubbleBinding?.iconYoutube?.setOnClickListener {
            showAdThenShowWindow(
                "https://m.youtube.com",
                "YouTube"
            )
        }
        bubbleBinding?.iconChatGpt?.setOnClickListener {
            showAdThenShowWindow(
                "https://chat.openai.com",
                "ChatGPT"
            )
        }
        bubbleBinding?.iconGoogleTranslator?.setOnClickListener {
            showAdThenShowWindow(
                "https://translate.google.com/",
                "Google Translate"
            )
        }
        bubbleBinding?.iconCustomUrl?.setOnClickListener {
            CustomUrlManager.getUrl()
                ?.let { showAdThenShowWindow(it, CustomUrlManager.getTitle() ?: "Custom App") }
        }
    }

    private fun showAdThenShowWindow(url: String, title: String) {
        val intent = Intent(this, AdActivity::class.java).apply {
            putExtra("URL_TO_OPEN", url)
            putExtra("TITLE_TO_OPEN", title)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }


    private fun togglePanel(expand: Boolean) {
        bubbleBinding?.let { binding ->
            val expandedPanel = binding.expandedPanel
            val collapsedHandle = binding.collapsedHandle

            if (expand) {
                collapsedHandle.visibility = View.GONE
                expandedPanel.visibility = View.VISIBLE
                expandedPanel.alpha = 0f
                expandedPanel.post {
                    expandedPanel.translationX = -expandedPanel.width.toFloat()
                    expandedPanel.animate()
                        .translationX(0f)
                        .alpha(1f)
                        .setDuration(300)
                        .start()
                }
            } else {

                expandedPanel.animate()
                    .translationX(-expandedPanel.width.toFloat())
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        expandedPanel.visibility = View.GONE
                        collapsedHandle.visibility = View.VISIBLE
                    }
                    .start()
            }
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Float Music Bubble Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Float Music Bubble").setSmallIcon(R.drawable.app_logo).build()
    }

    private inner class WebAppInterface(private val instance: FloatingWindowInstance) {
        @JavascriptInterface
        fun handleVideoClick(videoId: String) {
            instance.currentVideoId = videoId
            Log.d(TAG, "JavaScript Bridge Called! Video ID: $videoId for window ${instance.id}")
            val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&controls=0"
            Handler(Looper.getMainLooper()).post {
                instance.windowBinding.webView.loadUrl(
                    embedUrl,
                    mutableMapOf("Origin" to "https://www.youtube.com")
                )
                instance.windowBinding.buttonBack.visibility = View.VISIBLE
            }
        }
    }

    private inner class MyWebViewClient(private val instance: FloatingWindowInstance) :
        WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            instance.windowBinding.progressBar.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({
                val js = """
                javascript:(function() {
                    function setupEventListeners() {
                        document.querySelectorAll('a[href*="/watch?v="]').forEach(link => {
                            if (link.dataset.listenerAttached === 'true') return;
                            link.dataset.listenerAttached = 'true';
                            link.addEventListener('click', function(event) {
                                event.preventDefault();
                                try {
                                    const videoId = new URL(this.href).searchParams.get('v');
                                    if (videoId) AndroidInterface.handleVideoClick(videoId);
                                } catch(e) {}
                            });
                        });
                    }
                    setupEventListeners();
                    new MutationObserver(setupEventListeners).observe(document.body, { childList: true, subtree: true });
                })()
                """.trimIndent()
                view?.evaluateJavascript(js, null)
            }, 200)
        }
    }

    private inner class MyChromeClient(private val instance: FloatingWindowInstance) :
        WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            val progressBar = instance.windowBinding.progressBar
            if (newProgress < 100) {
                progressBar.visibility = View.VISIBLE
                progressBar.progress = newProgress
            } else {
                progressBar.visibility = View.GONE
            }
        }

        private var customViewCallback: CustomViewCallback? = null
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (instance.customView != null) {
                onHideCustomView(); return
            };
            super.onShowCustomView(view, callback)
            this.customViewCallback = callback
            instance.customView = view
            instance.windowBinding.webView.visibility = View.GONE
            instance.windowBinding.contentFrame.addView(view)
        }

        override fun onHideCustomView() {
            super.onHideCustomView()
            if (instance.customView == null) return
            if (instance.miniPlayerBinding != null) {
                switchToMainWindow(instance)
            } else {
                (instance.customView?.parent as? ViewGroup)?.removeView(instance.customView)
                instance.customView = null; customViewCallback =
                    null; instance.windowBinding.webView.visibility = View.VISIBLE
            }
        }
        fun exitFullScreen() {
            customViewCallback?.onCustomViewHidden()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        IS_SERVICE_RUNNING = false
        val windowIds = activeWindows.keys.toList()
        windowIds.forEach { closeWindow(it) }
        activeWindows.clear()
        try {
            bubbleBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            closeTargetBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        bubbleBinding = null
        closeTargetBinding = null
        Log.d(TAG, "FloatingService destroyed.")
    }
}