package com.buzzingmountain.dingclock.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.buzzingmountain.dingclock.BuildConfig
import com.buzzingmountain.dingclock.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.versionText.text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }
}
