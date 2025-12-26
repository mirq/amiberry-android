/*
 * Amiberry Android Virtual Joystick Overlay
 *
 * Provides touch-based D-pad and fire buttons for Port 1 joystick control
 */

#ifndef ANDROID_VJOYSTICK_H
#define ANDROID_VJOYSTICK_H

#ifdef __ANDROID__

#include <SDL.h>

// Overlay state
enum AndroidOverlayState {
    OVERLAY_OFF = 0,
    OVERLAY_MOUSE = 1,
    OVERLAY_JOYSTICK = 2,
    OVERLAY_KEYBOARD = 3
};

// Initialize the virtual joystick system
void android_vjoystick_init(SDL_Renderer* renderer);

// Clean up resources
void android_vjoystick_quit();

// Render the virtual joystick overlay
void android_vjoystick_render(SDL_Renderer* renderer);

// Process touch events for the virtual joystick
// Returns true if the event was handled by the virtual joystick
bool android_vjoystick_handle_touch(const SDL_Event& event);

// Get/set overlay state
AndroidOverlayState android_overlay_get_state();
void android_overlay_set_state(AndroidOverlayState state);

// Cycle to next overlay state (OFF -> MOUSE -> JOYSTICK -> KEYBOARD -> OFF)
void android_overlay_cycle();

// Render the overlay toggle button
void android_overlay_button_render(SDL_Renderer* renderer);

// Process touch events for the overlay toggle button
// Returns true if the toggle button was pressed
bool android_overlay_button_handle_touch(const SDL_Event& event);

// Check if the virtual joystick is currently active
bool android_vjoystick_is_active();

// Get the device index of the virtual joystick in the joystick array
// Returns -1 if not found
int android_vjoystick_get_device_index();

// Send virtual joystick input through the device mapping system
// These functions are called by JNI callbacks
void android_vjoystick_set_axis(int axis, int value);  // axis: 0=horiz, 1=vert; value: -1,0,1
void android_vjoystick_set_button(int button, bool pressed);  // button: 0=fire, 1=2nd

// Send mouse input through the input system
// These functions are called by JNI callbacks for mouse mode
void android_vmouse_set_button(int button, bool pressed);  // button: 0=left, 1=right
void android_vmouse_set_delta(float dx, float dy);  // relative mouse movement

#endif // __ANDROID__

#endif // ANDROID_VJOYSTICK_H
