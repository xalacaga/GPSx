package com.gpsx.tv

import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.gpsx.tv.remoteplay.ControllerMapper
import com.gpsx.tv.remoteplay.RemotePlayEngine
import com.gpsx.tv.ui.GpsxApp
import com.gpsx.tv.ui.GpsxTheme

class MainActivity : ComponentActivity() {
    private lateinit var remotePlayEngine: RemotePlayEngine
    private lateinit var controllerMapper: ControllerMapper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        remotePlayEngine = RemotePlayEngine(applicationContext)
        controllerMapper = ControllerMapper(remotePlayEngine::submitControllerState, onQuitChord = remotePlayEngine::stop)
        setContent {
            GpsxTheme {
                GpsxApp(remotePlayEngine)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val gameController =
            event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                event.source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK
        if (gameController && event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            return if (remotePlayEngine.isStreaming()) controllerMapper.key(event) else true
        }
        if (remotePlayEngine.isStreaming() && controllerMapper.key(event)) return true
        // A pad button that reaches the UI during a session can act as Back and end the stream:
        // log it (no "key" wording, which the live log filters) to see why it was not consumed.
        if (gameController && remotePlayEngine.isStreaming()) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                Log.w("GPSxPad", "pad code ${event.keyCode} scan 0x${event.scanCode.toString(16)} not sent to the game; ignored")
            }
            // Never let a pad button reach the UI during a stream, where it could act as Back and end it.
            // The remote's Back and the L1+R1+Options chord still leave.
            return true
        }
        if (gameController && event.action == KeyEvent.ACTION_DOWN && remotePlayEngine.snapshot().cloudTitle != null) {
            Log.w("GPSxPad", "pad code ${event.keyCode} scan 0x${event.scanCode.toString(16)} reached the UI (phase ${remotePlayEngine.snapshot().phase})")
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (remotePlayEngine.isStreaming() && controllerMapper.motion(event)) true else super.dispatchGenericMotionEvent(event)

    override fun onDestroy() {
        if (::remotePlayEngine.isInitialized) remotePlayEngine.close()
        super.onDestroy()
    }
}

