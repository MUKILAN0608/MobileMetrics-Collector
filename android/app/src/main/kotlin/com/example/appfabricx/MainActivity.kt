package com.example.appfabricx

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {

    companion object {
        var channel: MethodChannel? = null
    }

    private val CHANNEL_NAME = "appfabric/channel"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        channel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_NAME)
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, SystemMonitorService::class.java)
        startForegroundService(intent)
    }
}