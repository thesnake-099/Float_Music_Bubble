package com.meni.bubble_app

import android.view.WindowManager
import com.meni.bubble_app.databinding.LayoutFloatingWindowBinding
import com.meni.bubble_app.databinding.LayoutMiniBubbleBinding

/**
 * A data class to hold all state information for a single, independent floating window.
 */
data class FloatingWindow(
    val id: String, // The URL, used as a unique identifier
    val title: String,
    val binding: LayoutFloatingWindowBinding,
    val layoutParams: WindowManager.LayoutParams,
    var miniBubbleBinding: LayoutMiniBubbleBinding? = null,
    var miniBubbleLayoutParams: WindowManager.LayoutParams? = null,
    var isMinimized: Boolean = false,
    var currentVideoId: String? = null // Each window has its own video state
)