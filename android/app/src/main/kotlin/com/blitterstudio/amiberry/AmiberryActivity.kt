package com.blitterstudio.amiberry

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RelativeLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.libsdl.app.SDLActivity

/**
 * AmiberryActivity - Main activity for the Amiberry Amiga emulator
 *
 * Extends SDLActivity which handles:
 * - SDL2 initialization and lifecycle
 * - Native library loading
 * - Input handling (touch, keyboard, controllers)
 * - OpenGL ES context management
 * 
 * Note: First-run folder selection is handled by LauncherActivity which
 * must be called first. This activity assumes the data path is already configured.
 */
class AmiberryActivity : SDLActivity() {

    companion object {
        private const val TAG = "AmiberryActivity"
        
        // Ratio of screen height for SDL surface in portrait mode (e.g., 0.55 = 55% at top)
        private const val PORTRAIT_SDL_HEIGHT_RATIO = 0.55f
    }

    private var virtualJoystickOverlay: VirtualJoystickOverlay? = null
    private var virtualKeyboardOverlay: VirtualKeyboardOverlay? = null
    private var isPortrait = false

    override fun getLibraries(): Array<String> {
        return arrayOf(
            "SDL2",
            "SDL2_image",
            "SDL2_ttf",
            "amiberry"
        )
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        Log.v(TAG, "onCreate()")
        super.onCreate(savedInstanceState)

        // Go fullscreen and hide system UI
        hideSystemUI()

        // Keep screen on while emulating
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // Determine initial orientation
        isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        
        // Apply layout for current orientation
        applyOrientationLayout()
        
        // Add virtual joystick overlay on top of SDL surface
        addVirtualJoystickOverlay()
        
        // Add virtual keyboard overlay (initially hidden)
        addVirtualKeyboardOverlay()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        
        val newIsPortrait = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        if (newIsPortrait != isPortrait) {
            isPortrait = newIsPortrait
            Log.d(TAG, "Orientation changed: portrait=$isPortrait")
            applyOrientationLayout()
        }
    }
    
    private fun applyOrientationLayout() {
        val sdlLayout = mLayout as? RelativeLayout ?: return
        val sdlSurface = mSurface ?: return
        
        if (isPortrait) {
            // Portrait mode: SDL surface at top portion of screen
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                // We'll adjust the height after the layout is measured
                addRule(RelativeLayout.ALIGN_PARENT_TOP)
            }
            sdlSurface.layoutParams = params
            
            // Post a runnable to adjust height after layout is measured
            sdlLayout.post {
                val screenHeight = sdlLayout.height
                val sdlHeight = (screenHeight * PORTRAIT_SDL_HEIGHT_RATIO).toInt()
                
                val newParams = RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    sdlHeight
                ).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_TOP)
                }
                sdlSurface.layoutParams = newParams
                
                Log.d(TAG, "Portrait mode: SDL height=$sdlHeight (screen=$screenHeight)")
            }
        } else {
            // Landscape mode: SDL surface fills screen
            val params = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            sdlSurface.layoutParams = params
            
            Log.d(TAG, "Landscape mode: SDL fills screen")
        }
    }

    private fun addVirtualJoystickOverlay() {
        // Get the SDL layout (mLayout from SDLActivity)
        val sdlLayout = mLayout
        if (sdlLayout == null) {
            Log.e(TAG, "SDL layout not available")
            return
        }

        // Create the virtual joystick overlay
        virtualJoystickOverlay = VirtualJoystickOverlay(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Set callbacks for debugging and keyboard visibility
            onStateChanged = { state ->
                Log.d(TAG, "Overlay state changed: $state")
                // Show/hide keyboard based on state
                virtualKeyboardOverlay?.visibility = if (state == VirtualJoystickOverlay.STATE_KEYBOARD) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
            onJoystickChanged = { up, down, left, right ->
                Log.v(TAG, "Joystick: up=$up down=$down left=$left right=$right")
            }
            onButtonChanged = { button, pressed ->
                Log.v(TAG, "Button $button: $pressed")
            }
            onMouseButtonChanged = { button, pressed ->
                Log.v(TAG, "Mouse button $button: $pressed")
            }
            onMouseMoved = { dx, dy ->
                Log.v(TAG, "Mouse delta: dx=$dx dy=$dy")
            }
            onF12Pressed = {
                Log.d(TAG, "F12 button pressed")
                // Send F12 key press using same approach as actual keyboard
                // KEYCODE_F12 = 142 (Android keycode for F12)
                SDLActivity.onNativeKeyDown(142)
                SDLActivity.onNativeKeyUp(142)
            }
        }

        // Add overlay on top of SDL surface
        sdlLayout.addView(virtualJoystickOverlay)
        Log.d(TAG, "Virtual joystick overlay added")
    }

    private fun addVirtualKeyboardOverlay() {
        val sdlLayout = mLayout
        if (sdlLayout == null) {
            Log.e(TAG, "SDL layout not available for keyboard")
            return
        }

        // Create the virtual keyboard overlay
        virtualKeyboardOverlay = VirtualKeyboardOverlay(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            visibility = View.GONE // Initially hidden
            
            onKeyEvent = { keyCode, pressed ->
                Log.v(TAG, "Keyboard key: code=0x${keyCode.toString(16)} pressed=$pressed")
            }
        }

        // Add keyboard overlay on top of SDL surface (below joystick overlay)
        sdlLayout.addView(virtualKeyboardOverlay)
        Log.d(TAG, "Virtual keyboard overlay added")
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() - Cleaning up resources")
        
        // Clean up overlays to prevent memory leaks
        virtualJoystickOverlay = null
        virtualKeyboardOverlay = null
        
        // Call parent cleanup
        super.onDestroy()
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
