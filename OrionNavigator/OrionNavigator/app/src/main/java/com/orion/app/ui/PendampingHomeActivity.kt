package com.orion.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.orion.app.R
import java.util.Locale

/**
 * Pendamping Home Screen - Caregiver Feature Selection
 * 
 * Features:
 * 1. Live Lokasi - Open tracking map (PendampingActivity)
 * 2. Buat Peta AR - Open AR camera in MAP mode (DirectionActivity)
 */
class PendampingHomeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var cardLiveLocation: MaterialCardView
    private lateinit var cardCreateMap: MaterialCardView
    private lateinit var btnLogout: ImageButton

    private var tts: TextToSpeech? = null
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pendamping_home)

        initViews()
        setupListeners()
        initTts()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun initViews() {
        cardLiveLocation = findViewById(R.id.cardLiveLocation)
        cardCreateMap = findViewById(R.id.cardCreateMap)
        btnLogout = findViewById(R.id.btnLogout)
    }

    private fun initTts() {
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale("id", "ID")
            val audioAttributes = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttributes)
            tts?.speak("Menu Pendamping. Pilih Live Lokasi atau Buat Peta A R.", TextToSpeech.QUEUE_FLUSH, null, "welcome")
        }
    }

    private fun vibrate() {
        vibrator?.vibrate(50)
    }

    private fun setupListeners() {
        cardLiveLocation.setOnClickListener {
            vibrate()
            val intent = Intent(this, PendampingActivity::class.java)
            startActivity(intent)
        }

        cardCreateMap.setOnClickListener {
            vibrate()
            val intent = Intent(this, DirectionActivity::class.java)
            intent.putExtra("EXTRA_APP_MODE", "MAP")
            startActivity(intent)
        }

        btnLogout.setOnClickListener {
            vibrate()
            showLogoutDialog()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout))
            .setMessage(getString(R.string.logout_confirm))
            .setPositiveButton(getString(R.string.yes)) { _, _ ->
                val prefs = getSharedPreferences(SplashActivity.PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(SplashActivity.KEY_USER_ROLE).apply()
                val intent = Intent(this, RoleSelectionActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.no), null)
            .show()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
