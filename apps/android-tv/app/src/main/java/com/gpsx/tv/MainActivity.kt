package com.gpsx.tv

import android.os.Bundle
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
        controllerMapper = ControllerMapper(remotePlayEngine::submitControllerState)
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
        return if (remotePlayEngine.isStreaming() && controllerMapper.key(event)) true else super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (remotePlayEngine.isStreaming() && controllerMapper.motion(event)) true else super.dispatchGenericMotionEvent(event)

    override fun onDestroy() {
        if (::remotePlayEngine.isInitialized) remotePlayEngine.close()
        super.onDestroy()
    }
}

