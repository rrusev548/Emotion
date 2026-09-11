package com.emotion.pet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.emotion.pet.databinding.ActivityOnboardingBinding

/** Показва се веднъж, преди MainActivity — запознава с Peta и пита за разрешение за известия. */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finishOnboarding() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.notifRow.visibility =
            if (needsNotificationPermission()) android.view.View.VISIBLE else android.view.View.GONE

        binding.continueBtn.setOnClickListener {
            if (needsNotificationPermission()) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                finishOnboarding()
            }
        }
    }

    private fun needsNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
    }

    private fun finishOnboarding() {
        Prefs(this).onboarded = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
