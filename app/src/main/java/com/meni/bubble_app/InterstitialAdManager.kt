package com.meni.bubble_app

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object InterstitialAdManager {
    private const val TAG = "InterstitialAdManager"
    private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private var mInterstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    fun loadAd(context: Context) {
        if (isAdLoading || mInterstitialAd != null) { return }
        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(context.applicationContext, AD_UNIT_ID, adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    mInterstitialAd = interstitialAd
                    isAdLoading = false
                    Log.d(TAG, "Interstitial Ad was loaded.")
                }
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                    isAdLoading = false
                    Log.d(TAG, "Interstitial Ad failed to load: ${loadAdError.message}")
                }
            })
    }

    fun showAdIfReady(activity: Activity, onAdDismissed: () -> Unit) {
        if (SubscriptionManager.isSubscribed.value == true) {
            Log.d(TAG, "User has premium. Skipping ad.")
            onAdDismissed()
            return
        }
        if (mInterstitialAd != null && !activity.isFinishing) {
            mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    mInterstitialAd = null
                    loadAd(activity)
                    onAdDismissed()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    mInterstitialAd = null
                    loadAd(activity)
                    onAdDismissed()
                }
            }
            mInterstitialAd?.show(activity)
        } else {
            Log.d(TAG, "The interstitial ad wasn't ready yet.")
            loadAd(activity)
            onAdDismissed()
        }
    }
}