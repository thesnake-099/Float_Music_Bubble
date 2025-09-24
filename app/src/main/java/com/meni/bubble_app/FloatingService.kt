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
import com.meni.bubble_app.databinding.LayoutCloseTargetBinding
import com.meni.bubble_app.databinding.LayoutFloatingBubbleBinding
import com.meni.bubble_app.databinding.LayoutFloatingWindowBinding
import com.meni.bubble_app.databinding.LayoutMiniBubbleBinding
import com.meni.bubble_app.databinding.LayoutMiniPlayerBinding
import kotlin.math.abs


class FloatingService : Service() {

    companion object {
        var IS_SERVICE_RUNNING = false

        var isYoutubeWindowOpen = false

        private const val NOTIFICATION_CHANNEL_ID = "FloatingServiceChannel"
        private const val PREFS_NAME = "FloatingBubblePrefs"
        private const val KEY_BUBBLE_Y = "bubble_y"
        private const val TAG = "FloatingService"
        private const val YOUTUBE_HOME_URL = "https://m.youtube.com"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var sharedPreferences: SharedPreferences
    private var bubbleBinding: LayoutFloatingBubbleBinding? = null
    private var windowBinding: LayoutFloatingWindowBinding? = null
    private var bubbleLayoutParams: WindowManager.LayoutParams? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var customView: View? = null
    private var isWindowMinimized = false
    private var myChromeClient: MyChromeClient? = null
    private var miniPlayerBinding: LayoutMiniPlayerBinding? = null
    private var miniPlayerLayoutParams: WindowManager.LayoutParams? = null
    private var currentUrl: String? = null
    private var miniBubbleBinding: LayoutMiniBubbleBinding? = null
    private var miniBubbleLayoutParams: WindowManager.LayoutParams? = null
    private var closeTargetBinding: LayoutCloseTargetBinding? = null
    private var closeTargetLayoutParams: WindowManager.LayoutParams? = null
    private var currentVideoId: String? = null
    private var closeTargetRect: Rect? = null
    private var lastTouchRawX: Float = 0f
    private var lastTouchRawY: Float = 0f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, createNotification())
        IS_SERVICE_RUNNING = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        createBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("url")?.let { showWindow(it, "Smart Bubble") }
        return START_NOT_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun showWindow(url: String, title: String) {

        this.currentUrl = url
        isYoutubeWindowOpen = url.contains("youtube.com")

        togglePanel(false)

        if (windowBinding == null) {
            windowBinding = LayoutFloatingWindowBinding.inflate(LayoutInflater.from(this))
            val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            windowLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            windowBinding?.webView?.apply {
                webViewClient = MyWebViewClient()
                myChromeClient = MyChromeClient()
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
                addJavascriptInterface(WebAppInterface(), "AndroidInterface")
            }
            windowManager.addView(windowBinding?.root, windowLayoutParams)
            setupWindowTouchListeners(); setupWindowControls()
        }

        currentVideoId = null
        windowBinding?.buttonBack?.visibility = View.GONE
        windowBinding?.webView?.loadUrl(url)
        windowBinding?.windowTitle?.text = title
        windowBinding?.root?.visibility = View.VISIBLE
        windowBinding?.controlsContainer?.visibility = View.VISIBLE
        isWindowMinimized = false
    }

    private fun setupWindowControls() {
        windowBinding?.buttonClose?.setOnClickListener {
            if (miniPlayerBinding != null) switchToMainWindow()
            if (customView != null) myChromeClient?.exitFullScreen()
            windowBinding?.root?.visibility = View.GONE
            windowBinding?.controlsContainer?.visibility = View.GONE
            if (currentUrl?.contains("youtube.com") == true) {
                isYoutubeWindowOpen = false
            }
            currentUrl = null
        }
        windowBinding?.buttonMinimize?.setOnClickListener {
            if (currentUrl?.contains("youtube.com") == true) {
                switchToMiniPlayer()
            } else {
                switchToMiniBubble()
            }
        }
        windowBinding?.buttonBack?.setOnClickListener {
            if (currentVideoId != null) {
                windowBinding?.webView?.loadUrl(YOUTUBE_HOME_URL)
                currentVideoId = null
                windowBinding?.buttonBack?.visibility = View.GONE
            } else if (customView != null) {
                myChromeClient?.exitFullScreen()
            } else if (windowBinding?.webView?.canGoBack() == true) {
                windowBinding?.webView?.goBack()
            }
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    private fun setupWindowTouchListeners() {
        val moveListener = object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = windowLayoutParams!!.x; initialY = windowLayoutParams!!.y
                        initialTouchX = event.rawX; initialTouchY = event.rawY; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        windowLayoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        windowLayoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(windowBinding?.root, windowLayoutParams)
                        return true
                    }
                }
                return false
            }
        }
        windowBinding?.moveHandle?.setOnTouchListener(moveListener)
        windowBinding?.titleBar?.setOnTouchListener(moveListener)
        windowBinding?.resizeHandleBottomRight?.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0; private var initialHeight = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val windowRoot = windowBinding?.windowRoot ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = windowRoot.layoutParams.width; initialHeight = windowRoot.layoutParams.height
                        initialTouchX = event.rawX; initialTouchY = event.rawY; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newWidth = initialWidth + (event.rawX - initialTouchX).toInt()
                        val newHeight = initialHeight + (event.rawY - initialTouchY).toInt()
                        if (newWidth > 400 && newHeight > 300) {
                            val params = windowRoot.layoutParams
                            params.width = newWidth; params.height = newHeight
                            windowRoot.layoutParams = params
                        }
                        return true
                    }
                }
                return false
            }
        })
        windowBinding?.resizeHandleBottomLeft?.setOnTouchListener(object : View.OnTouchListener {
            private var initialWidth = 0; private var initialHeight = 0
            private var initialX = 0; private var initialTouchX = 0f; private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                val windowRoot = windowBinding?.windowRoot ?: return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialWidth = windowRoot.layoutParams.width; initialHeight = windowRoot.layoutParams.height
                        initialX = windowLayoutParams!!.x; initialTouchX = event.rawX; initialTouchY = event.rawY; return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val newWidth = initialWidth - dx
                        val newHeight = initialHeight + (event.rawY - initialTouchY).toInt()

                        if (newWidth > 400 && newHeight > 300) {
                            windowLayoutParams!!.x = initialX + dx
                            val params = windowRoot.layoutParams
                            params.width = newWidth
                            params.height = newHeight
                            windowRoot.layoutParams = params
                            windowManager.updateViewLayout(windowBinding?.root, windowLayoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun switchToMainWindow() {
        val viewToMove = customView ?: windowBinding?.webView ?: return
        (viewToMove.parent as? ViewGroup)?.removeView(viewToMove)
        windowBinding?.contentFrame?.addView(viewToMove)
        (viewToMove as? WebView)?.onResume()
        miniPlayerBinding?.root?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        miniPlayerBinding = null
        windowBinding?.root?.visibility = View.VISIBLE
        windowBinding?.controlsContainer?.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun switchToMiniPlayer() {
        val viewToMove = customView ?: windowBinding?.webView ?: return
        windowBinding?.root?.visibility = View.GONE
        windowBinding?.controlsContainer?.visibility = View.GONE
        if (miniPlayerBinding == null) {
            miniPlayerBinding = LayoutMiniPlayerBinding.inflate(LayoutInflater.from(this))
            val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            miniPlayerLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END; x = 16; y = 16
            }
            windowManager.addView(miniPlayerBinding?.root, miniPlayerLayoutParams)
        }
        (viewToMove.parent as? ViewGroup)?.removeView(viewToMove)
        miniPlayerBinding?.videoContainer?.addView(viewToMove)
        (viewToMove as? WebView)?.onResume()
        miniPlayerBinding?.closeMiniPlayerButton?.setOnClickListener { switchToMainWindow() }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun switchToMiniBubble() {
        windowBinding?.root?.visibility = View.GONE
        windowBinding?.controlsContainer?.visibility = View.GONE
        if (miniBubbleBinding == null) {
            miniBubbleBinding = LayoutMiniBubbleBinding.inflate(LayoutInflater.from(this))
            val layoutFlag =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            miniBubbleLayoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.CENTER }
            windowManager.addView(miniBubbleBinding?.root, miniBubbleLayoutParams)
            // The call to set up the listener was missing here.
            setupMiniBubbleTouchListener()
        }
        val iconResId = when {
            currentUrl?.contains("youtube.com") == true -> R.drawable.ic_youtube
            currentUrl?.contains("chat.openai.com") == true -> R.drawable.ic_chatgpt
            currentUrl?.contains("translate.google.com") == true -> R.drawable.ic_googletrans
            else -> R.drawable.ic_web
        }
        miniBubbleBinding?.miniBubbleIcon?.setImageResource(iconResId)
    }

    private fun switchToMainWindowFromBubble() {
        miniBubbleBinding?.root?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
            }
        }
        miniBubbleBinding = null
        windowBinding?.root?.visibility = View.VISIBLE
        windowBinding?.controlsContainer?.visibility = View.VISIBLE
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMiniBubbleTouchListener() {
        miniBubbleBinding?.root?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0;
            private var initialY = 0;
            private var initialTouchX = 0f;
            private var initialTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = miniBubbleLayoutParams!!.x
                        initialY = miniBubbleLayoutParams!!.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        lastTouchRawX = event.rawX
                        lastTouchRawY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        lastTouchRawX = event.rawX
                        lastTouchRawY = event.rawY

                        miniBubbleLayoutParams!!.x = initialX + (event.rawX - initialTouchX).toInt()
                        miniBubbleLayoutParams!!.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(
                            miniBubbleBinding?.root,
                            miniBubbleLayoutParams
                        )
                        showCloseTarget()
                        val isOver =
                            closeTargetRect?.contains(lastTouchRawX.toInt(), lastTouchRawY.toInt())
                                ?: isBubbleOverCloseTarget()
                        updateCloseTargetVisual(isOver)
                        Log.d(
                            TAG,
                            "MOVE raw=(${lastTouchRawX.toInt()},${lastTouchRawY.toInt()}) closeRect=$closeTargetRect isOver=$isOver"
                        )

                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val wasOver =
                            closeTargetRect?.contains(event.rawX.toInt(), event.rawY.toInt())
                                ?: isBubbleOverCloseTarget()

                        hideCloseTarget()

                        if (wasOver) {
                            Log.d(TAG, "Bubble dropped over close target -> stopping service")
                            stopSelf()
                        } else if (abs(event.rawX - initialTouchX) < 10 && abs(event.rawY - initialTouchY) < 10) {
                            switchToMainWindowFromBubble()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun isBubbleOverCloseTarget(): Boolean {
        if (closeTargetBinding == null || miniBubbleBinding == null) return false
        try {
            val closeLocation = IntArray(2)
            closeTargetBinding?.root?.getLocationOnScreen(closeLocation)
            val closeTargetRectLocal = Rect(
                closeLocation[0],
                closeLocation[1],
                closeLocation[0] + (closeTargetBinding?.root?.width ?: 0),
                closeLocation[1] + (closeTargetBinding?.root?.height ?: 0)
            )
            val bubbleLocation = IntArray(2)
            miniBubbleBinding?.root?.getLocationOnScreen(bubbleLocation)
            val bubbleRect = Rect(
                bubbleLocation[0],
                bubbleLocation[1],
                bubbleLocation[0] + (miniBubbleBinding?.root?.width ?: 0),
                bubbleLocation[1] + (miniBubbleBinding?.root?.height ?: 0)
            )
            val intersects = Rect.intersects(closeTargetRectLocal, bubbleRect)
            Log.d(
                TAG,
                "isBubbleOverCloseTarget fallback: close=$closeTargetRectLocal bubble=$bubbleRect intersects=$intersects"
            )
            return intersects
        } catch (e: Exception) {
            Log.w(TAG, "isBubbleOverCloseTarget failed: ${e.message}")
            return false
        }
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
            ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 50 }
        }
        try {
            val root = closeTargetBinding?.root
            if (root != null && root.windowToken == null && !(root.isAttachedToWindow)) {
                windowManager.addView(root, closeTargetLayoutParams)
                root.alpha = 0.7f
                root.scaleX = 1.0f
                root.scaleY = 1.0f
            }
            closeTargetBinding?.root?.post {
                try {
                    val loc = IntArray(2)
                    closeTargetBinding?.root?.getLocationOnScreen(loc)
                    val w =
                        closeTargetBinding?.root?.width ?: closeTargetBinding?.root?.measuredWidth
                        ?: 0
                    val h =
                        closeTargetBinding?.root?.height ?: closeTargetBinding?.root?.measuredHeight
                        ?: 0
                    closeTargetRect = Rect(loc[0], loc[1], loc[0] + w, loc[1] + h)
                    Log.d(TAG, "showCloseTarget -> cached rect=$closeTargetRect")
                    val over =
                        closeTargetRect?.contains(lastTouchRawX.toInt(), lastTouchRawY.toInt())
                            ?: false
                    updateCloseTargetVisual(over)
                } catch (e: Exception) {
                    Log.w(TAG, "post-layout rect caching failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "showCloseTarget addView failed: ${e.message}")
        }
    }

    private fun hideCloseTarget() {
        try {
            closeTargetBinding?.root?.let { view ->
                try {
                    if (view.isAttachedToWindow) windowManager.removeView(view)
                } catch (e: Exception) {
                    // ignore
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "hideCloseTarget: ${e.message}")
        } finally {
            closeTargetBinding = null
            closeTargetRect = null
        }
    }

    private fun updateCloseTargetVisual(isOver: Boolean) {
        closeTargetBinding?.root?.let { root ->
            try {
                root.alpha = if (isOver) 1.0f else 0.7f
                val scale = if (isOver) 1.2f else 1.0f
                root.scaleX = scale
                root.scaleY = scale
            } catch (_: Exception) {
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
        bubbleBinding?.iconYoutube?.setOnClickListener { showAdThenShowWindow("https://m.youtube.com") }
        bubbleBinding?.iconChatGpt?.setOnClickListener { showAdThenShowWindow("https://chat.openai.com") }
        bubbleBinding?.iconGoogleTranslator?.setOnClickListener { showAdThenShowWindow("https://translate.google.com/") }
        bubbleBinding?.iconCustomUrl?.setOnClickListener {
            CustomUrlManager.getUrl()?.let { showAdThenShowWindow(it) }
        }
    }

    private fun showAdThenShowWindow(url: String) {
        togglePanel(false)
        val intent = Intent(this, AdActivity::class.java).apply {
            putExtra("URL_TO_OPEN", url); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun togglePanel(expand: Boolean) {
        if (expand) {
            bubbleBinding?.collapsedHandle?.visibility =
                View.GONE; bubbleBinding?.expandedPanel?.visibility = View.VISIBLE
        } else {
            bubbleBinding?.collapsedHandle?.visibility =
                View.VISIBLE; bubbleBinding?.expandedPanel?.visibility = View.GONE
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Floating Bubble Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Smart Bubble Active").setSmallIcon(R.drawable.ic_bubble).build()
    }

    private inner class WebAppInterface {
        @JavascriptInterface
        fun handleVideoClick(videoId: String) {
            this@FloatingService.currentVideoId = videoId
            Log.d(TAG, "JavaScript Bridge Called! Video ID: $videoId")
            val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&controls=0"
            val headers = mutableMapOf<String, String>()
            headers["Origin"] = "https://www.youtube.com"
            Handler(Looper.getMainLooper()).post {
                windowBinding?.webView?.loadUrl(embedUrl, headers)
                windowBinding?.buttonBack?.visibility = View.VISIBLE
            }
        }
    }

    private inner class MyWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            windowBinding?.progressBar?.visibility = View.GONE
            Handler(Looper.getMainLooper()).postDelayed({
                val js = """
                javascript:(function() {
                    function setupEventListeners() {
                        var links = document.querySelectorAll('a[href*="/watch?v="]');
                        for (var i = 0; i < links.length; i++) {
                            if (links[i].getAttribute('data-listener-attached') === 'true') continue;
                            links[i].setAttribute('data-listener-attached', 'true');
                            links[i].addEventListener('click', function(event) {
                                event.preventDefault();
                                try {
                                    var videoUrl = new URL(this.href);
                                    var videoId = videoUrl.searchParams.get('v');
                                    if (videoId) { AndroidInterface.handleVideoClick(videoId); }
                                } catch(e) {}
                            });
                        }
                    }
                    setupEventListeners();
                    var observer = new MutationObserver(setupEventListeners);
                    observer.observe(document.body, { childList: true, subtree: true });
                })()
                """.trimIndent()
                view?.evaluateJavascript(js, null)
            }, 200)
        }
    }

    private inner class MyChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            super.onProgressChanged(view, newProgress)
            if (newProgress < 100) {
                windowBinding?.progressBar?.visibility = View.VISIBLE
                windowBinding?.progressBar?.progress = newProgress
            } else {
                windowBinding?.progressBar?.visibility = View.GONE
            }
        }

        private var customViewCallback: CustomViewCallback? = null
        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) {
                onHideCustomView(); return
            }; super.onShowCustomView(view, callback); this.customViewCallback =
                callback; customView = view; windowBinding?.webView?.visibility =
                View.GONE; windowBinding?.contentFrame?.addView(view)
        }

        override fun onHideCustomView() {
            super.onHideCustomView()
            if (customView == null) return
            if (miniPlayerBinding != null) {
                switchToMainWindow()
            } else {
                (customView?.parent as? ViewGroup)?.removeView(customView)
                customView = null; customViewCallback = null; windowBinding?.webView?.visibility =
                    View.VISIBLE
            }
        }

        fun exitFullScreen() {
            customViewCallback?.onCustomViewHidden()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        IS_SERVICE_RUNNING = false

        isYoutubeWindowOpen = false

        try {
            bubbleBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            windowBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            miniPlayerBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            miniBubbleBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        try {
            closeTargetBinding?.root?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
        }
        bubbleBinding = null; windowBinding = null; miniPlayerBinding = null; miniBubbleBinding =
            null; closeTargetBinding = null
    }
}
