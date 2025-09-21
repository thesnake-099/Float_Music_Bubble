package com.meni.bubble_app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.meni.bubble_app.databinding.ActivitySubscriptionBinding

class SubscriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySubscriptionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySubscriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
        observeSubscriptionStatus()
    }

    private fun setupClickListeners() {
        binding.backButton.setOnClickListener {
            finish() // Close this activity
        }

        binding.upgradeButton.setOnClickListener {
            SubscriptionManager.purchase(this)
        }

        binding.restoreButton.setOnClickListener {
            binding.progressBar.visibility = View.VISIBLE
            SubscriptionManager.restorePurchases(
                onSuccess = {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Purchases restored successfully!", Toast.LENGTH_SHORT).show()
                },
                onError = { message ->
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, "Restore failed: $message", Toast.LENGTH_LONG).show()
                }
            )
        }

        binding.saveUrlButton.setOnClickListener {
            val url = binding.customUrlEditText.text.toString().trim()
            val title = binding.customTitleEditText.text.toString().trim()
            if (url.isNotEmpty() && title.isNotEmpty()) {
                CustomUrlManager.save(url, title)
                Toast.makeText(this, "Custom URL saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter both a title and a URL.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.clearUrlButton.setOnClickListener {
            CustomUrlManager.clear()
            binding.customUrlEditText.text?.clear()
            binding.customTitleEditText.text?.clear()
            Toast.makeText(this, "Custom URL cleared!", Toast.LENGTH_SHORT).show()
        }


    }

    private fun observeSubscriptionStatus() {
        SubscriptionManager.isSubscribed.observe(this) { isSubscribed ->
            binding.progressBar.visibility = View.GONE
            if (isSubscribed) {
                binding.statusTextView.text = "Current Status: Premium"
                binding.upgradeButton.visibility = View.GONE
                // Show the premium feature UI
                binding.premiumFeaturesLayout.visibility = View.VISIBLE
                // Load any previously saved data
                binding.customUrlEditText.setText(CustomUrlManager.getUrl())
                binding.customTitleEditText.setText(CustomUrlManager.getTitle())
            } else {
                binding.statusTextView.text = "Current Status: Free"
                binding.upgradeButton.visibility = View.VISIBLE
                // Hide the premium feature UI
                binding.premiumFeaturesLayout.visibility = View.GONE
            }
        }

        // Observe the offering to show the price on the button
        SubscriptionManager.currentOffering.observe(this) { currentPackage ->
            if (currentPackage != null) {
                binding.upgradeButton.isEnabled = true
                val price = currentPackage.product.price.formatted
                binding.upgradeButton.text = "Upgrade to Premium ($price)"
            } else {
                binding.upgradeButton.isEnabled = false
                binding.upgradeButton.text = "Subscription Not Available"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Check status every time the user comes to this screen
        binding.progressBar.visibility = View.VISIBLE
        SubscriptionManager.checkSubscriptionStatus()
    }
}