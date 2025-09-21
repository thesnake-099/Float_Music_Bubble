package com.meni.bubble_app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AdActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val urlToOpen = intent.getStringExtra("URL_TO_OPEN")
        val titleToOpen = intent.getStringExtra("TITLE_TO_OPEN") // Get the title // Get the title

        if (urlToOpen == null) {
            finish()
            return
        }

        // --- THIS IS THE NEW LOGIC ---
        // Check the global state before showing an ad or starting the service.
        if (urlToOpen.contains("youtube.com") && FloatingService.isAnyYoutubeWindowOpen()) {
            launchNativeYouTube()
            finish() // Close this transparent activity.
            return
        }

        InterstitialAdManager.showAdIfReady(this) {
            val serviceIntent = Intent(this, FloatingService::class.java).apply {
                putExtra("url", urlToOpen)
                putExtra("title", titleToOpen) // Pass the title
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            finish()
        }
    }


    // --- NEW HELPER METHOD ---
    private fun launchNativeYouTube() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            stopService(Intent(this, FloatingService::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "YouTube app not found.", Toast.LENGTH_SHORT).show()
        }
    }
}