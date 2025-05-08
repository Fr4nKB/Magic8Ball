package com.frank.magic8ball

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkOverlayPermission()
        val serviceConnection = object : android.content.ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                Log.d("MainActivity", "onServiceConnected")
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Log.d("MainActivity", "onServiceDisconnected")
            }
        }
        val intent2 = Intent(this, ToyService::class.java)
        bindService(intent2, serviceConnection, BIND_AUTO_CREATE)

    }

    fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val myIntent: Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(myIntent)
            }
        }
    }


}
