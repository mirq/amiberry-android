package com.blitterstudio.amiberry

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Virtual Joystick Overlay for touch-based joystick control
 * 
 * Provides:
 * - Toggle button (top-right) to show/hide controls
 * - D-pad (bottom-left) for directional input
 * - Fire buttons A & B (bottom-right)
 * - Plus/Minus buttons to resize controls
 */
class VirtualJoystickOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "VJoystickOverlay"
        
        // Overlay states
        const val STATE_OFF = 0
        const val STATE_JOYSTICK = 1
        const val STATE_KEYBOARD = 2
        
        // Layout ratios (relative to screen height)
        private const val DPAD_SIZE_RATIO = 0.30f
        private const val BUTTON_SIZE_RATIO = 0.12f
        private const val TOGGLE_SIZE_RATIO = 0.07f
        private const val MARGIN_RATIO = 0.03f
        
        // D-pad dead zone ratio
        private const val DPAD_DEADZONE = 0.25f
        
        // Scale limits
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 2.0f
        private const val SCALE_STEP = 0.1f
        
        // SharedPreferences key
        private const val PREFS_NAME = "VirtualJoystickPrefs"
        private const val PREF_SCALE = "control_scale"
        private const val DEFAULT_SCALE = 1.0f
    }

    // SharedPreferences for persisting scale
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Current scale factor
    private var scaleFactor: Float = prefs.getFloat(PREF_SCALE, DEFAULT_SCALE)

    // Current state
    private var overlayState = STATE_OFF
    
    // Paints
    private val dpadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 80, 80, 80)
        style = Paint.Style.FILL
    }
    private val dpadArrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 200, 200, 200)
        style = Paint.Style.FILL
    }
    private val dpadHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(100, 100, 200, 100)
        style = Paint.Style.FILL
    }
    private val buttonAPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 200, 50, 50)
        style = Paint.Style.FILL
    }
    private val buttonBPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 50, 50, 200)
        style = Paint.Style.FILL
    }
    private val buttonPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val togglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 60, 60, 60)
        style = Paint.Style.FILL
    }
    private val toggleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 3f
    }
    private val resizeButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 60, 60, 60)
        style = Paint.Style.FILL
    }
    private val resizeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    
    // Rectangles for hit testing
    private val dpadRect = RectF()
    private val buttonARect = RectF()
    private val buttonBRect = RectF()
    private val toggleRect = RectF()
    private val plusRect = RectF()
    private val minusRect = RectF()
    private val f12Rect = RectF()
    
    // D-pad state
    private var dpadUp = false
    private var dpadDown = false
    private var dpadLeft = false
    private var dpadRight = false
    
    // Button states
    private var buttonAPressed = false
    private var buttonBPressed = false
    
    // Touch tracking
    private var dpadPointerId = -1
    private var buttonAPointerId = -1
    private var buttonBPointerId = -1
    
    // Callback for state changes
    var onStateChanged: ((Int) -> Unit)? = null
    var onJoystickChanged: ((Boolean, Boolean, Boolean, Boolean) -> Unit)? = null
    var onButtonChanged: ((Int, Boolean) -> Unit)? = null
    var onF12Pressed: (() -> Unit)? = null

    init {
        // Make view clickable to receive touch events
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateLayout(w, h)
    }

    private fun updateLayout(w: Int, h: Int) {
        val margin = (h * MARGIN_RATIO).toInt()
        
        // D-pad (bottom-left) - affected by scale
        val dpadSize = (h * DPAD_SIZE_RATIO * scaleFactor).toInt()
        dpadRect.set(
            margin.toFloat(),
            (h - dpadSize - margin).toFloat(),
            (margin + dpadSize).toFloat(),
            (h - margin).toFloat()
        )
        
        // Buttons (bottom-right) - affected by scale
        val buttonSize = (h * BUTTON_SIZE_RATIO * scaleFactor).toInt()
        
        // Button A (primary fire) - bottom right
        buttonARect.set(
            (w - buttonSize - margin).toFloat(),
            (h - buttonSize - margin).toFloat(),
            (w - margin).toFloat(),
            (h - margin).toFloat()
        )
        
        // Button B (secondary fire) - above and left of A
        buttonBRect.set(
            (w - buttonSize * 2 - margin * 2).toFloat(),
            (h - buttonSize * 2 - margin).toFloat(),
            (w - buttonSize - margin * 2).toFloat(),
            (h - buttonSize - margin).toFloat()
        )
        
        // Toggle button (top-right) - not affected by scale
        val toggleSize = (h * TOGGLE_SIZE_RATIO).toInt()
        toggleRect.set(
            (w - toggleSize - margin).toFloat(),
            margin.toFloat(),
            (w - margin).toFloat(),
            (margin + toggleSize).toFloat()
        )
        
        // Plus button (left of toggle)
        val resizeButtonSize = (toggleSize * 0.8f).toInt()
        plusRect.set(
            (w - toggleSize - margin - resizeButtonSize - margin / 2).toFloat(),
            margin.toFloat(),
            (w - toggleSize - margin - margin / 2).toFloat(),
            (margin + resizeButtonSize).toFloat()
        )
        
        // Minus button (left of plus)
        minusRect.set(
            (w - toggleSize - margin - resizeButtonSize * 2 - margin).toFloat(),
            margin.toFloat(),
            (w - toggleSize - margin - resizeButtonSize - margin).toFloat(),
            (margin + resizeButtonSize).toFloat()
        )

        // F12 button (left of minus)
        f12Rect.set(
            (w - toggleSize - margin - resizeButtonSize * 3 - margin * 2).toFloat(),
            margin.toFloat(),
            (w - toggleSize - margin - resizeButtonSize * 2 - margin * 2).toFloat(),
            (margin + resizeButtonSize).toFloat()
        )
        
        // Update text size for resize buttons
        resizeTextPaint.textSize = resizeButtonSize * 0.7f
        
        Log.d(TAG, "Layout updated: w=$w h=$h scale=$scaleFactor dpad=$dpadRect toggle=$toggleRect")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Always draw toggle button and resize buttons
        drawToggleButton(canvas)
        drawResizeButtons(canvas)
        
        // Draw joystick controls if in joystick state
        if (overlayState == STATE_JOYSTICK) {
            drawDpad(canvas)
            drawButtons(canvas)
        }
    }

    private fun drawToggleButton(canvas: Canvas) {
        // Draw circular background
        val cx = toggleRect.centerX()
        val cy = toggleRect.centerY()
        val radius = toggleRect.width() / 2
        
        // Brighter when active
        togglePaint.color = if (overlayState != STATE_OFF) {
            Color.argb(220, 80, 80, 80)
        } else {
            Color.argb(180, 60, 60, 60)
        }
        canvas.drawCircle(cx, cy, radius, togglePaint)
        
        // Draw hamburger lines
        val lineWidth = radius * 1.2f
        val lineSpacing = radius * 0.35f
        val lineHeight = 3f
        
        for (i in -1..1) {
            canvas.drawRect(
                cx - lineWidth / 2,
                cy + i * lineSpacing - lineHeight / 2,
                cx + lineWidth / 2,
                cy + i * lineSpacing + lineHeight / 2,
                toggleLinePaint
            )
        }
    }
    
    private fun drawResizeButtons(canvas: Canvas) {
        // Plus button
        val plusCx = plusRect.centerX()
        val plusCy = plusRect.centerY()
        val plusRadius = plusRect.width() / 2
        canvas.drawCircle(plusCx, plusCy, plusRadius, resizeButtonPaint)
        canvas.drawText("+", plusCx, plusCy - (resizeTextPaint.descent() + resizeTextPaint.ascent()) / 2, resizeTextPaint)

        // Minus button
        val minusCx = minusRect.centerX()
        val minusCy = minusRect.centerY()
        val minusRadius = minusRect.width() / 2
        canvas.drawCircle(minusCx, minusCy, minusRadius, resizeButtonPaint)
        canvas.drawText("-", minusCx, minusCy - (resizeTextPaint.descent() + resizeTextPaint.ascent()) / 2, resizeTextPaint)

        // F12 button
        val f12Cx = f12Rect.centerX()
        val f12Cy = f12Rect.centerY()
        val f12Radius = f12Rect.width() / 2
        canvas.drawCircle(f12Cx, f12Cy, f12Radius, resizeButtonPaint)
        canvas.drawText("F12", f12Cx, f12Cy - (resizeTextPaint.descent() + resizeTextPaint.ascent()) / 2, resizeTextPaint)
    }

    private fun drawDpad(canvas: Canvas) {
        val cx = dpadRect.centerX()
        val cy = dpadRect.centerY()
        val size = dpadRect.width()
        val third = size / 3
        
        // Draw cross shape
        // Vertical bar
        canvas.drawRect(
            cx - third / 2, dpadRect.top,
            cx + third / 2, dpadRect.bottom,
            dpadPaint
        )
        // Horizontal bar
        canvas.drawRect(
            dpadRect.left, cy - third / 2,
            dpadRect.right, cy + third / 2,
            dpadPaint
        )
        
        // Highlight pressed directions
        if (dpadUp) {
            canvas.drawRect(
                cx - third / 2, dpadRect.top,
                cx + third / 2, dpadRect.top + third,
                dpadHighlightPaint
            )
        }
        if (dpadDown) {
            canvas.drawRect(
                cx - third / 2, dpadRect.bottom - third,
                cx + third / 2, dpadRect.bottom,
                dpadHighlightPaint
            )
        }
        if (dpadLeft) {
            canvas.drawRect(
                dpadRect.left, cy - third / 2,
                dpadRect.left + third, cy + third / 2,
                dpadHighlightPaint
            )
        }
        if (dpadRight) {
            canvas.drawRect(
                dpadRect.right - third, cy - third / 2,
                dpadRect.right, cy + third / 2,
                dpadHighlightPaint
            )
        }
    }

    private fun drawButtons(canvas: Canvas) {
        // Button A
        val radiusA = buttonARect.width() / 2
        canvas.drawCircle(buttonARect.centerX(), buttonARect.centerY(), radiusA, buttonAPaint)
        if (buttonAPressed) {
            canvas.drawCircle(buttonARect.centerX(), buttonARect.centerY(), radiusA - 2, buttonPressedPaint)
        }
        
        // Button B
        val radiusB = buttonBRect.width() / 2
        canvas.drawCircle(buttonBRect.centerX(), buttonBRect.centerY(), radiusB, buttonBPaint)
        if (buttonBPressed) {
            canvas.drawCircle(buttonBRect.centerX(), buttonBRect.centerY(), radiusB - 2, buttonPressedPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val pointerIndex = event.actionIndex
        val pointerId = event.getPointerId(pointerIndex)
        val x = event.getX(pointerIndex)
        val y = event.getY(pointerIndex)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // Check toggle button first - always active
                if (toggleRect.contains(x, y)) {
                    cycleState()
                    return true
                }
                
                // Check resize buttons - always active
                if (plusRect.contains(x, y)) {
                    increaseScale()
                    return true
                }
                if (minusRect.contains(x, y)) {
                    decreaseScale()
                    return true
                }
                if (f12Rect.contains(x, y)) {
                    onF12Pressed?.invoke()
                    return true
                }
                
                // Handle joystick controls only if in JOYSTICK state
                if (overlayState == STATE_JOYSTICK) {
                    if (dpadRect.contains(x, y) && dpadPointerId == -1) {
                        dpadPointerId = pointerId
                        updateDpadState(x, y)
                        return true
                    }
                    if (buttonARect.contains(x, y) && buttonAPointerId == -1) {
                        buttonAPointerId = pointerId
                        setButtonAPressed(true)
                        return true
                    }
                    if (buttonBRect.contains(x, y) && buttonBPointerId == -1) {
                        buttonBPointerId = pointerId
                        setButtonBPressed(true)
                        return true
                    }
                }
                
                // If not handled, pass through to underlying view
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                // Only handle if we're tracking a pointer
                if (dpadPointerId != -1 || buttonAPointerId != -1 || buttonBPointerId != -1) {
                    // Update all active pointers
                    for (i in 0 until event.pointerCount) {
                        val pid = event.getPointerId(i)
                        val px = event.getX(i)
                        val py = event.getY(i)
                        
                        if (pid == dpadPointerId) {
                            updateDpadState(px, py)
                        }
                    }
                    return true
                }
                return false
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                var handled = false
                if (pointerId == dpadPointerId) {
                    dpadPointerId = -1
                    clearDpadState()
                    handled = true
                }
                if (pointerId == buttonAPointerId) {
                    buttonAPointerId = -1
                    setButtonAPressed(false)
                    handled = true
                }
                if (pointerId == buttonBPointerId) {
                    buttonBPointerId = -1
                    setButtonBPressed(false)
                    handled = true
                }
                return handled
            }
        }
        
        return false
    }
    
    private fun increaseScale() {
        if (scaleFactor < MAX_SCALE) {
            scaleFactor = (scaleFactor + SCALE_STEP).coerceAtMost(MAX_SCALE)
            saveScale()
            updateLayout(width, height)
            invalidate()
            Log.d(TAG, "Scale increased to: $scaleFactor")
        }
    }
    
    private fun decreaseScale() {
        if (scaleFactor > MIN_SCALE) {
            scaleFactor = (scaleFactor - SCALE_STEP).coerceAtLeast(MIN_SCALE)
            saveScale()
            updateLayout(width, height)
            invalidate()
            Log.d(TAG, "Scale decreased to: $scaleFactor")
        }
    }
    
    private fun saveScale() {
        prefs.edit().putFloat(PREF_SCALE, scaleFactor).apply()
    }

    private fun updateDpadState(x: Float, y: Float) {
        val cx = dpadRect.centerX()
        val cy = dpadRect.centerY()
        val radius = dpadRect.width() / 2
        
        val dx = x - cx
        val dy = y - cy
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        
        // Check if outside deadzone
        val deadzone = radius * DPAD_DEADZONE
        
        val newUp: Boolean
        val newDown: Boolean
        val newLeft: Boolean
        val newRight: Boolean
        
        if (distance < deadzone) {
            newUp = false
            newDown = false
            newLeft = false
            newRight = false
        } else {
            // Calculate angle and determine direction
            val angle = atan2(dy.toDouble(), dx.toDouble())
            val deg = Math.toDegrees(angle)
            
            // 8-way directional with 45-degree segments
            newRight = deg > -67.5 && deg < 67.5
            newDown = deg > 22.5 && deg < 157.5
            newLeft = deg > 112.5 || deg < -112.5
            newUp = deg > -157.5 && deg < -22.5
        }
        
        if (newUp != dpadUp || newDown != dpadDown || newLeft != dpadLeft || newRight != dpadRight) {
            dpadUp = newUp
            dpadDown = newDown
            dpadLeft = newLeft
            dpadRight = newRight
            
            onJoystickChanged?.invoke(dpadUp, dpadDown, dpadLeft, dpadRight)
            nativeSetJoystick(dpadUp, dpadDown, dpadLeft, dpadRight)
            invalidate()
        }
    }

    private fun clearDpadState() {
        if (dpadUp || dpadDown || dpadLeft || dpadRight) {
            dpadUp = false
            dpadDown = false
            dpadLeft = false
            dpadRight = false
            
            onJoystickChanged?.invoke(false, false, false, false)
            nativeSetJoystick(false, false, false, false)
            invalidate()
        }
    }

    private fun setButtonAPressed(pressed: Boolean) {
        if (buttonAPressed != pressed) {
            buttonAPressed = pressed
            onButtonChanged?.invoke(0, pressed)
            nativeSetButton(0, pressed)
            invalidate()
        }
    }

    private fun setButtonBPressed(pressed: Boolean) {
        if (buttonBPressed != pressed) {
            buttonBPressed = pressed
            onButtonChanged?.invoke(1, pressed)
            nativeSetButton(1, pressed)
            invalidate()
        }
    }

    private fun cycleState() {
        overlayState = when (overlayState) {
            STATE_OFF -> STATE_JOYSTICK
            STATE_JOYSTICK -> STATE_KEYBOARD
            else -> STATE_OFF
        }
        
        Log.d(TAG, "State changed to: $overlayState")
        onStateChanged?.invoke(overlayState)
        nativeSetOverlayState(overlayState)
        
        // Clear any pressed states when hiding
        if (overlayState != STATE_JOYSTICK) {
            clearDpadState()
            setButtonAPressed(false)
            setButtonBPressed(false)
            dpadPointerId = -1
            buttonAPointerId = -1
            buttonBPointerId = -1
        }
        
        invalidate()
    }

    fun setState(state: Int) {
        if (overlayState != state) {
            overlayState = state
            invalidate()
        }
    }

    fun getState(): Int = overlayState

    // Native methods - will be implemented in android_vjoystick.cpp
    private external fun nativeSetJoystick(up: Boolean, down: Boolean, left: Boolean, right: Boolean)
    private external fun nativeSetButton(button: Int, pressed: Boolean)
    private external fun nativeSetOverlayState(state: Int)
    private external fun nativeSetKey(keyCode: Int, pressed: Boolean)
}
