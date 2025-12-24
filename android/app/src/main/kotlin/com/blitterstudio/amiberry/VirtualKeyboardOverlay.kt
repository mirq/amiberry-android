package com.blitterstudio.amiberry

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View

/**
 * Virtual Keyboard Overlay - Android native Amiga keyboard
 *
 * Displays a virtual Amiga keyboard at the bottom of the screen
 * that sends Amiga key codes via JNI.
 */
class VirtualKeyboardOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "VKeyboardOverlay"
        
        init {
            // Ensure native library is loaded for JNI calls
            // This is technically redundant since SDLActivity loads it, but ensures
            // the class can find the native method even if loaded early
            try {
                System.loadLibrary("amiberry")
                android.util.Log.d(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                // Library may already be loaded by SDLActivity - that's OK
                android.util.Log.d(TAG, "Native library already loaded or load deferred: ${e.message}")
            }
        }
        
        // Amiga key codes (from keyboard.h AK_* defines)
        const val AK_A = 0x20
        const val AK_B = 0x35
        const val AK_C = 0x33
        const val AK_D = 0x22
        const val AK_E = 0x12
        const val AK_F = 0x23
        const val AK_G = 0x24
        const val AK_H = 0x25
        const val AK_I = 0x17
        const val AK_J = 0x26
        const val AK_K = 0x27
        const val AK_L = 0x28
        const val AK_M = 0x37
        const val AK_N = 0x36
        const val AK_O = 0x18
        const val AK_P = 0x19
        const val AK_Q = 0x10
        const val AK_R = 0x13
        const val AK_S = 0x21
        const val AK_T = 0x14
        const val AK_U = 0x16
        const val AK_V = 0x34
        const val AK_W = 0x11
        const val AK_X = 0x32
        const val AK_Y = 0x15
        const val AK_Z = 0x31
        
        const val AK_0 = 0x0A
        const val AK_1 = 0x01
        const val AK_2 = 0x02
        const val AK_3 = 0x03
        const val AK_4 = 0x04
        const val AK_5 = 0x05
        const val AK_6 = 0x06
        const val AK_7 = 0x07
        const val AK_8 = 0x08
        const val AK_9 = 0x09
        
        const val AK_SPC = 0x40
        const val AK_RET = 0x44
        const val AK_BS = 0x41
        const val AK_DEL = 0x46
        const val AK_ESC = 0x45
        const val AK_TAB = 0x42
        
        const val AK_UP = 0x4C
        const val AK_DN = 0x4D
        const val AK_LF = 0x4F
        const val AK_RT = 0x4E
        
        const val AK_LSH = 0x60
        const val AK_RSH = 0x61
        const val AK_CTRL = 0x63
        const val AK_LALT = 0x64
        const val AK_RALT = 0x65
        const val AK_LAMI = 0x66
        const val AK_RAMI = 0x67
        
        const val AK_F1 = 0x50
        const val AK_F2 = 0x51
        const val AK_F3 = 0x52
        const val AK_F4 = 0x53
        const val AK_F5 = 0x54
        const val AK_F6 = 0x55
        const val AK_F7 = 0x56
        const val AK_F8 = 0x57
        const val AK_F9 = 0x58
        const val AK_F10 = 0x59
        
        const val AK_HELP = 0x5F
        
        const val AK_MINUS = 0x0B
        const val AK_EQUAL = 0x0C
        const val AK_BACKSLASH = 0x0D
        const val AK_LBRACKET = 0x1A
        const val AK_RBRACKET = 0x1B
        const val AK_SEMICOLON = 0x29
        const val AK_QUOTE = 0x2A
        const val AK_BACKQUOTE = 0x00
        const val AK_COMMA = 0x38
        const val AK_PERIOD = 0x39
        const val AK_SLASH = 0x3A
    }

    // Key definition: label, amiga keycode, width multiplier
    data class KeyDef(val label: String, val keyCode: Int, val width: Float = 1f)

    // Keyboard layout - simplified Amiga layout
    private val keyboardLayout = listOf(
        // Row 1: ESC, F1-F10, DEL, HELP
        listOf(
            KeyDef("Esc", AK_ESC),
            KeyDef("F1", AK_F1), KeyDef("F2", AK_F2), KeyDef("F3", AK_F3), KeyDef("F4", AK_F4), KeyDef("F5", AK_F5),
            KeyDef("F6", AK_F6), KeyDef("F7", AK_F7), KeyDef("F8", AK_F8), KeyDef("F9", AK_F9), KeyDef("F10", AK_F10),
            KeyDef("Del", AK_DEL), KeyDef("Help", AK_HELP)
        ),
        // Row 2: ` 1-0 - = \ BS
        listOf(
            KeyDef("`", AK_BACKQUOTE),
            KeyDef("1", AK_1), KeyDef("2", AK_2), KeyDef("3", AK_3), KeyDef("4", AK_4), KeyDef("5", AK_5),
            KeyDef("6", AK_6), KeyDef("7", AK_7), KeyDef("8", AK_8), KeyDef("9", AK_9), KeyDef("0", AK_0),
            KeyDef("-", AK_MINUS), KeyDef("=", AK_EQUAL), KeyDef("\\", AK_BACKSLASH), KeyDef("BS", AK_BS)
        ),
        // Row 3: TAB Q-P [ ] RET
        listOf(
            KeyDef("Tab", AK_TAB, 1.5f),
            KeyDef("Q", AK_Q), KeyDef("W", AK_W), KeyDef("E", AK_E), KeyDef("R", AK_R), KeyDef("T", AK_T),
            KeyDef("Y", AK_Y), KeyDef("U", AK_U), KeyDef("I", AK_I), KeyDef("O", AK_O), KeyDef("P", AK_P),
            KeyDef("[", AK_LBRACKET), KeyDef("]", AK_RBRACKET), KeyDef("Ret", AK_RET, 1.5f)
        ),
        // Row 4: CTRL A-L ; ' cursor keys
        listOf(
            KeyDef("Ctrl", AK_CTRL, 1.5f),
            KeyDef("A", AK_A), KeyDef("S", AK_S), KeyDef("D", AK_D), KeyDef("F", AK_F), KeyDef("G", AK_G),
            KeyDef("H", AK_H), KeyDef("J", AK_J), KeyDef("K", AK_K), KeyDef("L", AK_L),
            KeyDef(";", AK_SEMICOLON), KeyDef("'", AK_QUOTE),
            KeyDef("\u2191", AK_UP), // Up arrow
        ),
        // Row 5: SHIFT Z-M , . / SHIFT, cursor keys
        listOf(
            KeyDef("Shift", AK_LSH, 2f),
            KeyDef("Z", AK_Z), KeyDef("X", AK_X), KeyDef("C", AK_C), KeyDef("V", AK_V), KeyDef("B", AK_B),
            KeyDef("N", AK_N), KeyDef("M", AK_M),
            KeyDef(",", AK_COMMA), KeyDef(".", AK_PERIOD), KeyDef("/", AK_SLASH),
            KeyDef("\u2190", AK_LF), KeyDef("\u2193", AK_DN), KeyDef("\u2192", AK_RT) // Arrow keys
        ),
        // Row 6: Alt, Amiga, Space, Amiga, Alt
        listOf(
            KeyDef("Alt", AK_LALT, 1.5f),
            KeyDef("A", AK_LAMI, 1.5f), // Left Amiga
            KeyDef("Space", AK_SPC, 6f),
            KeyDef("A", AK_RAMI, 1.5f), // Right Amiga
            KeyDef("Alt", AK_RALT, 1.5f)
        )
    )

    // Computed key rectangles
    private data class KeyRect(val rect: RectF, val keyDef: KeyDef)
    private val keyRects = mutableListOf<KeyRect>()

    // Paints
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 50, 50, 50)
        style = Paint.Style.FILL
    }
    private val keyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 80, 80, 80)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 100, 100, 200)
        style = Paint.Style.FILL
    }
    private val keyTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.MONOSPACE
    }
    private val stickyKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 150, 100, 50)
        style = Paint.Style.FILL
    }

    // Keyboard dimensions
    private var keyboardTop = 0f
    private var keyWidth = 0f
    private var keyHeight = 0f
    private var keySpacing = 2f

    // State tracking
    private val pressedKeys = mutableSetOf<Int>() // Currently pressed key codes
    private val stickyKeys = setOf(AK_LSH, AK_RSH, AK_CTRL, AK_LALT, AK_RALT, AK_LAMI, AK_RAMI)
    private val activeStickyKeys = mutableSetOf<Int>() // Sticky keys that are toggled on
    private val pointerToKey = mutableMapOf<Int, Int>() // pointer ID -> key code

    // Callback for key events
    var onKeyEvent: ((Int, Boolean) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateKeyLayout(w, h)
    }

    private fun calculateKeyLayout(w: Int, h: Int) {
        keyRects.clear()
        
        // Keyboard takes bottom 40% of screen
        val keyboardHeight = h * 0.40f
        keyboardTop = h - keyboardHeight
        
        val numRows = keyboardLayout.size
        keyHeight = (keyboardHeight - keySpacing * (numRows + 1)) / numRows
        keyTextPaint.textSize = keyHeight * 0.4f
        
        // Calculate base key width from longest row
        val maxUnitsInRow = keyboardLayout.maxOfOrNull { row -> 
            row.sumOf { it.width.toDouble() }.toFloat() 
        } ?: 13f
        keyWidth = (w - keySpacing * (maxUnitsInRow.toInt() + 1)) / maxUnitsInRow
        
        var y = keyboardTop + keySpacing
        
        for (row in keyboardLayout) {
            val rowWidth = row.sumOf { it.width.toDouble() }.toFloat()
            var x = (w - rowWidth * keyWidth - (row.size - 1) * keySpacing) / 2
            
            for (keyDef in row) {
                val kw = keyDef.width * keyWidth + (keyDef.width - 1) * keySpacing
                val rect = RectF(x, y, x + kw, y + keyHeight)
                keyRects.add(KeyRect(rect, keyDef))
                x += kw + keySpacing
            }
            y += keyHeight + keySpacing
        }
        
        Log.d(TAG, "Keyboard layout calculated: ${keyRects.size} keys, top=$keyboardTop")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw semi-transparent background
        canvas.drawRect(0f, keyboardTop, width.toFloat(), height.toFloat(), 
            Paint().apply { color = Color.argb(200, 30, 30, 30) })
        
        // Draw each key
        for (keyRect in keyRects) {
            val isPressed = pressedKeys.contains(keyRect.keyDef.keyCode)
            val isStickyActive = activeStickyKeys.contains(keyRect.keyDef.keyCode)
            
            // Draw key background
            val paint = when {
                isPressed -> keyPressedPaint
                isStickyActive -> stickyKeyPaint
                else -> keyPaint
            }
            canvas.drawRoundRect(keyRect.rect, 8f, 8f, paint)
            canvas.drawRoundRect(keyRect.rect, 8f, 8f, keyBorderPaint)
            
            // Draw key label
            val textX = keyRect.rect.centerX()
            val textY = keyRect.rect.centerY() - (keyTextPaint.descent() + keyTextPaint.ascent()) / 2
            canvas.drawText(keyRect.keyDef.label, textX, textY, keyTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val x = event.getX(pointerIndex)
                val y = event.getY(pointerIndex)
                
                // Check if touch is in keyboard area
                if (y < keyboardTop) {
                    return false // Pass through
                }
                
                // Find which key was pressed
                val keyRect = keyRects.find { it.rect.contains(x, y) }
                if (keyRect != null) {
                    val keyCode = keyRect.keyDef.keyCode
                    handleKeyPress(keyCode, pointerId)
                    return true
                }
                return true // Consume touch in keyboard area even if no key hit
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Handle finger sliding between keys
                for (i in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(i)
                    val x = event.getX(i)
                    val y = event.getY(i)
                    
                    val currentKey = pointerToKey[pointerId]
                    val newKeyRect = keyRects.find { it.rect.contains(x, y) }
                    val newKeyCode = newKeyRect?.keyDef?.keyCode
                    
                    if (currentKey != newKeyCode) {
                        // Finger moved to different key
                        currentKey?.let { handleKeyRelease(it, pointerId) }
                        newKeyCode?.let { handleKeyPress(it, pointerId) }
                    }
                }
                return true
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                
                pointerToKey[pointerId]?.let { keyCode ->
                    handleKeyRelease(keyCode, pointerId)
                }
                return true
            }
        }
        return false
    }

    private fun handleKeyPress(keyCode: Int, pointerId: Int) {
        pointerToKey[pointerId] = keyCode
        
        if (stickyKeys.contains(keyCode)) {
            // Toggle sticky key
            if (activeStickyKeys.contains(keyCode)) {
                activeStickyKeys.remove(keyCode)
                sendKeyEvent(keyCode, false)
            } else {
                activeStickyKeys.add(keyCode)
                sendKeyEvent(keyCode, true)
            }
        } else {
            // Regular key press
            pressedKeys.add(keyCode)
            sendKeyEvent(keyCode, true)
        }
        invalidate()
    }

    private fun handleKeyRelease(keyCode: Int, pointerId: Int) {
        pointerToKey.remove(pointerId)
        
        if (!stickyKeys.contains(keyCode)) {
            // Only release non-sticky keys
            pressedKeys.remove(keyCode)
            sendKeyEvent(keyCode, false)
            
            // Also release any active sticky keys after a regular key press
            for (stickyKey in activeStickyKeys.toList()) {
                activeStickyKeys.remove(stickyKey)
                sendKeyEvent(stickyKey, false)
            }
        }
        invalidate()
    }

    private fun sendKeyEvent(keyCode: Int, pressed: Boolean) {
        Log.d(TAG, "Key event: code=0x${keyCode.toString(16)} pressed=$pressed - calling native")
        onKeyEvent?.invoke(keyCode, pressed)
        try {
            nativeSetKey(keyCode, pressed)
            Log.d(TAG, "nativeSetKey returned successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Native method not linked: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Exception calling nativeSetKey: ${e.message}")
        }
    }

    fun clearAllKeys() {
        for (keyCode in pressedKeys.toList()) {
            pressedKeys.remove(keyCode)
            sendKeyEvent(keyCode, false)
        }
        for (keyCode in activeStickyKeys.toList()) {
            activeStickyKeys.remove(keyCode)
            sendKeyEvent(keyCode, false)
        }
        pointerToKey.clear()
        invalidate()
    }

    // Native method - implemented in android_vjoystick.cpp
    private external fun nativeSetKey(keyCode: Int, pressed: Boolean)
}
