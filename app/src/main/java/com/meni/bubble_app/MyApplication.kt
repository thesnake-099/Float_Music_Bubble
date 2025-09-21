package com.meni.bubble_app

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

class MyApplication : Application() {
    // IMPROVEMENT 8: Store your official signature hash here
    private val OFFICIAL_SIGNATURE = "w6Ou9CivtayRorf1zu0I7hTPAts="
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        InterstitialAdManager.loadAd(this)
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, "goog_YOUR_PUBLIC_API_KEY_HERE").build()
        )
        SubscriptionManager.initialize()
        CustomUrlManager.initialize(this)

        // Run the signature check
        if (!verifyAppSignature()) {
            // If the signature is invalid, prevent the app from running.
            // This is a simple way to stop a tampered app.
            android.os.Process.killProcess(android.os.Process.myPid())
            return
        }

    }

    private fun verifyAppSignature(): Boolean {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_SIGNATURES)
            for (signature in packageInfo.signatures!!) {
                val md = java.security.MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val currentSignature = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)

                Log.d("SIGNATURE_CHECK", "Current Signature: $currentSignature") // For debugging

                if (currentSignature == OFFICIAL_SIGNATURE) {
                    return true
                }
            }
        } catch (e: Exception) {
            return false // An error occurred, fail safely
        }
        return false
    }
}