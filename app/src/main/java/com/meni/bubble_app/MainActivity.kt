package com.meni.bubble_app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.ads.AdRequest
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.meni.bubble_app.databinding.ActivityMainBinding
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.shape.Circle
import com.takusemba.spotlight.shape.RoundedRectangle

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var actionToPerform: (() -> Unit)? = null
    private var hasRequestedPermission = false

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasOverlayPermission()) {
            actionToPerform?.invoke()
        } else {
            binding.bubbleSwitch.isChecked = false
        }
        actionToPerform = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        try {
            val packageInfo = packageManager.getPackageInfo(
                packageName,
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            for (signature in packageInfo.signatures!!) {
                val md = java.security.MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val digest = md.digest()
                Log.d(
                    "MY_APP_SIGNATURE",
                    android.util.Base64.encodeToString(digest, android.util.Base64.NO_WRAP)
                )
            }
        } catch (e: Exception) {
        }
        showTutorialIfFirstTime()

        val adRequest = AdRequest.Builder().build()
        binding.adView.loadAd(adRequest)

        binding.bubbleSwitch.isChecked = FloatingService.IS_SERVICE_RUNNING

        binding.bubbleSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                handleActionWithPermission {
                    startFloatingService(null, null)
                }
            } else {
                stopFloatingService()
            }
        }

        binding.cardYoutube.setOnClickListener {
            if (FloatingService.isAnyYoutubeWindowOpen()) {
                launchNativeYouTube()
            } else {
                handleActionWithPermission {
                    InterstitialAdManager.showAdIfReady(this) {
                        startFloatingService("https://m.youtube.com", "YouTube")
                    }
                }
            }
        }

        binding.cardChatGpt.setOnClickListener {
            handleActionWithPermission {
                InterstitialAdManager.showAdIfReady(this) {
                    startFloatingService("https://chat.openai.com", "ChatGPT")
                }
            }
        }

        binding.cardTrans.setOnClickListener {
            handleActionWithPermission {
                InterstitialAdManager.showAdIfReady(this) {
                    startFloatingService("https://translate.google.com/", "Google Translate")
                }
            }
        }

        binding.cardCustomUrl.setOnClickListener {
            val customUrl = CustomUrlManager.getUrl()
            if (customUrl != null) {
                handleActionWithPermission {
                    InterstitialAdManager.showAdIfReady(this) {
                        startFloatingService(customUrl, "Custom App")
                    }
                }
            }
        }

        binding.manageSubscriptionButton.setOnClickListener {
            val intent = Intent(this, SubscriptionActivity::class.java)
            startActivity(intent)
        }

        SubscriptionManager.isSubscribed.observe(this) { isSubscribed ->
            if (isSubscribed) {
                binding.manageSubscriptionButton.text = "You are a Premium User"
            } else {
                binding.manageSubscriptionButton.text = "Remove Ads (Go Premium)"
            }
        }
    }
    private fun launchNativeYouTube() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "YouTube app not found.", Toast.LENGTH_SHORT).show()
        }
    }
    private fun showTutorialIfFirstTime() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("isFirstLaunch", true)
        if (isFirstLaunch) {
            // We wait until the layout is fully drawn to ensure the views have dimensions.
            binding.root.post {
                runTutorialSequence()
                prefs.edit().putBoolean("isFirstLaunch", false).apply()
            }
        }
    }
    private fun runTutorialSequence() {
        val inflater = LayoutInflater.from(this)
        val switchOverlay = inflater.inflate(R.layout.layout_spotlight_target, null)
        switchOverlay.findViewById<TextView>(R.id.spotlight_title).text = "Turn on the Bubble"
        switchOverlay.findViewById<TextView>(R.id.spotlight_description).text =
            "This will enable the floating side panel for multitasking."
        val switchTarget = Target.Builder()
            .setAnchor(binding.bubbleSwitch)
            .setShape(Circle(90f))
            .setOverlay(switchOverlay)
            .build()
        val cardOverlay = inflater.inflate(R.layout.layout_spotlight_target, null)
        cardOverlay.findViewById<TextView>(R.id.spotlight_title).text = "Launch a Window"
        cardOverlay.findViewById<TextView>(R.id.spotlight_description).text =
            "You can also tap any of these cards to instantly open a floating window."
        val cardTarget = Target.Builder()
            .setAnchor(binding.cardYoutube)
            .setShape(
                RoundedRectangle(
                    binding.cardYoutube.height.toFloat(),
                    binding.cardYoutube.width.toFloat(),
                    16f * resources.displayMetrics.density
                )
            )
            .setOverlay(cardOverlay)
            .build()
        val spotlight = Spotlight.Builder(this)
            .setTargets(switchTarget, cardTarget)
            .setBackgroundColor(ContextCompat.getColor(this, R.color.spotlight_overlay))
            .setDuration(800L)
            .setAnimation(DecelerateInterpolator(2f))
            .build()
        switchOverlay.setOnClickListener { spotlight.next() }
        cardOverlay.setOnClickListener { spotlight.next() }
        spotlight.start()
    }
    override fun onResume() {
        super.onResume()
        SubscriptionManager.checkSubscriptionStatus()
        updateCustomUrlCard()
    }

    private fun updateCustomUrlCard() {
        if (CustomUrlManager.hasCustomUrl()) {
            binding.cardCustomUrl.visibility = View.VISIBLE
            binding.customUrlTitle.text = CustomUrlManager.getTitle()
        } else {
            binding.cardCustomUrl.visibility = View.GONE
        }
    }
    private fun handleActionWithPermission(action: () -> Unit) {
        if (hasOverlayPermission()) {
            action()
        } else {
            this.actionToPerform = action
            if (!hasRequestedPermission) {
                requestOverlayPermission()
            } else {
                Toast.makeText(
                    this,
                    "This feature requires the 'Display over other apps' permission.",
                    Toast.LENGTH_LONG
                ).show()
                binding.bubbleSwitch.isChecked = false
            }
        }
    }
    private fun startFloatingService(url: String?, title: String?, showPanel: Boolean = false) {
        if (!FloatingService.IS_SERVICE_RUNNING) {
            binding.bubbleSwitch.isChecked = true
        }
        val intent = Intent(this, FloatingService::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
            putExtra("SHOW_PANEL", showPanel)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopFloatingService() {
        binding.bubbleSwitch.isChecked = false
        stopService(Intent(this, FloatingService::class.java))
    }

    private fun hasOverlayPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        hasRequestedPermission = true
        MaterialAlertDialogBuilder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs 'Display over other apps' permission to function. Please grant it in the settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                binding.bubbleSwitch.isChecked = false
                dialog.dismiss()
            }
            .show()
    }


}