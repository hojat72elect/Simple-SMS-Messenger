package com.simplemobiletools.smsmessenger.activities

import android.content.Intent

class SplashActivity : BaseSplashActivity() {
    override fun initActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
