package com.ycsoft.printernt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ycsoft.printernt.ui.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ycsoft.printernt.R
import com.ycsoft.printernt.ads.core.RemoteBootstrap

class SplashActivity : AppCompatActivity() {

    private var moved = false   // chống gọi MainActivity nhiều lần

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        try {
            // ===== START LOG =====
            Log.d("SPLASH", "Start: call RemoteBootstrap.startAsync (background)")
            // ===== END LOG =====

            // ✅ Gọi core NGẦM: remote -> cache -> default
            // Link remoteUrl: anh có thể set ở đây, hoặc tốt hơn là để trong RemoteConfig.kt (core)
            // RemoteBootstrap.remoteUrl = "https://raw.githubusercontent.com/.../config.json"

            RemoteBootstrap.startAsync(this) { rootJson ->
                // ===== START LOG =====
                Log.d(
                    "SPLASH",
                    "Remote loaded. source=${RemoteBootstrap.lastSource} version=${RemoteBootstrap.lastVersion} updatedAt=${RemoteBootstrap.lastUpdatedAt}"
                )
                // ===== END LOG =====

                // rootJson đã hợp lệ (REMOTE/CACHE/DEFAULT).
                // Anh chưa cần show gì ở Splash, chỉ cần core cập nhật cache là đủ.
            }

        } catch (e: Exception) {
            // ===== START LOG =====
            Log.e("SPLASH_FATAL", "RemoteBootstrap startAsync error (ignored)", e)
            // ===== END LOG =====
        }

        // ⏱ Dù có mạng hay không → LUÔN đợi 3s rồi vào Main
        lifecycleScope.launch {
            try {
                delay(3000)
                goMainSafely()
            } catch (e: Exception) {
                // ===== START LOG =====
                Log.e("SPLASH_FATAL", "Crash prevented", e)
                // ===== END LOG =====
                goMainSafely()
            }
        }
    }

    // ================== HELPERS ==================

    private fun goMainSafely() {
        if (moved) return
        moved = true
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
