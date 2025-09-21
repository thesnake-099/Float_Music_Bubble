package com.meni.bubble_app

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.revenuecat.purchases.*
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener

object SubscriptionManager {

    private const val TAG = "SubscriptionManager"
    private const val ENTITLEMENT_ID = "premium"

    private val _isSubscribed = MutableLiveData<Boolean>()
    val isSubscribed: LiveData<Boolean> = _isSubscribed

    private val _currentOffering = MutableLiveData<Package?>()
    val currentOffering: LiveData<Package?> = _currentOffering

    fun initialize() {
        Purchases.sharedInstance.updatedCustomerInfoListener =
            UpdatedCustomerInfoListener { customerInfo ->
                updateSubscriptionStatus(customerInfo)
            }
        fetchOfferings()
        checkSubscriptionStatus()
    }

    private fun fetchOfferings() {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Log.e(TAG, "Error fetching offerings: $error")
                _currentOffering.postValue(null)
            },
            onSuccess = { offerings ->
                _currentOffering.postValue(offerings.current?.monthly)
            }
        )
    }

    fun checkSubscriptionStatus() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                Log.e(TAG, "Error fetching customer info: $error")
                _isSubscribed.postValue(false)
            },
            onSuccess = { customerInfo ->
                updateSubscriptionStatus(customerInfo)
            }
        )
    }

    private fun updateSubscriptionStatus(customerInfo: CustomerInfo) {
        val isPro = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
        _isSubscribed.postValue(isPro)
        Log.d(TAG, "Subscription status updated: Is Premium = $isPro")
    }

    fun purchase(activity: Activity) {
        val packageToPurchase = _currentOffering.value ?: return
        val purchaseParams = PurchaseParams.Builder(activity, packageToPurchase).build()

        Purchases.sharedInstance.purchaseWith(
            purchaseParams,
            onError = { error, userCancelled ->
                if (!userCancelled) {
                    Log.e(TAG, "Purchase failed: $error")
                }
            },
            onSuccess = { _, customerInfo ->
                Log.d(TAG, "Purchase successful!")
                updateSubscriptionStatus(customerInfo)
            }
        )
    }

    fun restorePurchases(onSuccess: () -> Unit, onError: (String) -> Unit) {
        Purchases.sharedInstance.restorePurchasesWith(
            onError = { error ->
                Log.e(TAG, "Restore failed: $error")
                onError(error.message)
            },
            onSuccess = { customerInfo ->
                Log.d(TAG, "Restore successful!")
                updateSubscriptionStatus(customerInfo)
                onSuccess()
            }
        )
    }
}