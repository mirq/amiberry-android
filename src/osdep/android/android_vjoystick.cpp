/*
 * Amiberry Android Virtual Joystick - JNI Bridge
 *
 * This file provides JNI functions called from the Android VirtualJoystickOverlay
 * to control both GUI navigation and emulated joystick.
 */

#ifdef __ANDROID__

#include <jni.h>
#include <SDL.h>
#include "android_vjoystick.h"

// Include for input event definitions
#include "sysconfig.h"
#include "sysdeps.h"
#include "inputdevice.h"
#include "options.h"
#include "keyboard.h"
#include "amiberry_input.h"

// GUI input for pushing fake key events
#include <guisan/sdl.hpp>
extern gcn::SDLInput* gui_input;

// Keyboard input function
extern void inputdevice_do_keyboard(int code, int state);

// Check if we're in GUI mode
extern bool gui_running;

// Overlay state
static AndroidOverlayState s_overlay_state = OVERLAY_OFF;

// Joystick state tracking
static bool s_dpad_up = false;
static bool s_dpad_down = false;
static bool s_dpad_left = false;
static bool s_dpad_right = false;
static bool s_button_a = false;
static bool s_button_b = false;

// Virtual joystick device ID for mapping through input system
static int s_virtual_joystick_device_id = -1;

// Use send_input_event for direct input injection during emulation
extern int send_input_event(int nr, int state, int max, int autofire);

// Access to the joystick device functions
extern void setjoybuttonstate(int joy, int button, int state);
extern void setjoystickstate(int joy, int axis, int state, int max);

// Push a fake SDL keyboard event via SDL event queue (for when gui_input isn't available)
static void push_sdl_key(SDL_Keycode key, bool pressed) {
    SDL_Event event{};
    event.type = pressed ? SDL_KEYDOWN : SDL_KEYUP;
    event.key.keysym.sym = key;
    event.key.state = pressed ? SDL_PRESSED : SDL_RELEASED;
    event.key.keysym.scancode = SDL_GetScancodeFromKey(key);
    event.key.keysym.mod = KMOD_NONE;
    event.key.timestamp = SDL_GetTicks();
    event.key.windowID = 0;
    event.key.repeat = 0;
    SDL_PushEvent(&event);
    SDL_Log("VJoystick: push_sdl_key(key=%d, pressed=%d)", key, pressed);
}

// JNI function: Set joystick direction
extern "C" JNIEXPORT void JNICALL
Java_com_blitterstudio_amiberry_VirtualJoystickOverlay_nativeSetJoystick(
    JNIEnv* env, jobject thiz,
    jboolean up, jboolean down, jboolean left, jboolean right)
{
    // Calculate axis values from D-pad state
    int horiz = 0;  // -1 = left, 0 = center, 1 = right
    int vert = 0;   // -1 = up, 0 = center, 1 = down
    
    if (left && !right) horiz = -1;
    else if (right && !left) horiz = 1;
    
    if (up && !down) vert = -1;
    else if (down && !up) vert = 1;
    
    // Send through device mapping system
    android_vjoystick_set_axis(0, horiz);  // Horizontal axis
    android_vjoystick_set_axis(1, vert);   // Vertical axis
    
    // Update state for GUI navigation (keep existing behavior)
    if (up != s_dpad_up) {
        s_dpad_up = up;
        push_sdl_key(SDLK_UP, up);
    }
    if (down != s_dpad_down) {
        s_dpad_down = down;
        push_sdl_key(SDLK_DOWN, down);
    }
    if (left != s_dpad_left) {
        s_dpad_left = left;
        push_sdl_key(SDLK_LEFT, left);
    }
    if (right != s_dpad_right) {
        s_dpad_right = right;
        push_sdl_key(SDLK_RIGHT, right);
    }
    
    SDL_Log("VJoystick JNI: up=%d down=%d left=%d right=%d (horiz=%d vert=%d)", 
            up, down, left, right, horiz, vert);
}

// JNI function: Set button state
extern "C" JNIEXPORT void JNICALL
Java_com_blitterstudio_amiberry_VirtualJoystickOverlay_nativeSetButton(
    JNIEnv* env, jobject thiz,
    jint button, jboolean pressed)
{
    if (button == 0) {
        // Button A = Fire / Enter
        if (pressed != s_button_a) {
            s_button_a = pressed;
            android_vjoystick_set_button(0, pressed);  // Fire button through device mapping
            push_sdl_key(SDLK_RETURN, pressed);  // GUI navigation
        }
    } else if (button == 1) {
        // Button B = 2nd button / Escape (for going back in GUI)
        if (pressed != s_button_b) {
            s_button_b = pressed;
            android_vjoystick_set_button(1, pressed);  // 2nd button through device mapping
            push_sdl_key(SDLK_ESCAPE, pressed);  // GUI navigation
        }
    }
    
    SDL_Log("VJoystick JNI: button=%d pressed=%d", button, pressed);
}

// JNI function: Set overlay state
extern "C" JNIEXPORT void JNICALL
Java_com_blitterstudio_amiberry_VirtualJoystickOverlay_nativeSetOverlayState(
    JNIEnv* env, jobject thiz,
    jint state)
{
    AndroidOverlayState old_state = s_overlay_state;
    s_overlay_state = static_cast<AndroidOverlayState>(state);
    
    SDL_Log("VJoystick JNI: state changed from %d to %d", old_state, state);
    
    // Clear joystick state when leaving joystick mode
    if (state != OVERLAY_JOYSTICK && old_state == OVERLAY_JOYSTICK) {
        // Clear all axes and buttons
        android_vjoystick_set_axis(0, 0);  // Center horizontal
        android_vjoystick_set_axis(1, 0);  // Center vertical
        android_vjoystick_set_button(0, false);  // Release fire
        android_vjoystick_set_button(1, false);  // Release 2nd button
        
        // Clear GUI keys
        if (s_dpad_up) { s_dpad_up = false; push_sdl_key(SDLK_UP, false); }
        if (s_dpad_down) { s_dpad_down = false; push_sdl_key(SDLK_DOWN, false); }
        if (s_dpad_left) { s_dpad_left = false; push_sdl_key(SDLK_LEFT, false); }
        if (s_dpad_right) { s_dpad_right = false; push_sdl_key(SDLK_RIGHT, false); }
        if (s_button_a) { s_button_a = false; push_sdl_key(SDLK_RETURN, false); }
        if (s_button_b) { s_button_b = false; push_sdl_key(SDLK_ESCAPE, false); }
    }
}

// Track active modifier states for shift handling
static bool s_shift_active = false;

// Push an SDL_TEXTINPUT event for text entry in GUI
static void push_sdl_text_input(char character) {
    SDL_Event event{};
    event.type = SDL_TEXTINPUT;
    event.text.timestamp = SDL_GetTicks();
    event.text.windowID = 0;
    // SDL_TEXTINPUT expects UTF-8 text, single ASCII char is valid UTF-8
    event.text.text[0] = character;
    event.text.text[1] = '\0';
    SDL_PushEvent(&event);
    SDL_Log("VKeyboard: push_sdl_text_input('%c')", character);
}

// Convert SDL keycode to ASCII character (returns 0 if not printable)
// shift parameter indicates if shift is active
static char sdl_key_to_char(SDL_Keycode key, bool shift) {
    // Letters
    if (key >= SDLK_a && key <= SDLK_z) {
        char base = 'a' + (key - SDLK_a);
        return shift ? (base - 32) : base;  // uppercase if shift
    }
    
    // Numbers and their shifted symbols
    if (key >= SDLK_0 && key <= SDLK_9) {
        if (!shift) {
            return '0' + (key - SDLK_0);
        }
        // Shifted number row symbols (US layout)
        const char* shifted = ")!@#$%^&*(";
        return shifted[key - SDLK_0];
    }
    
    // Punctuation (non-shifted and shifted)
    switch (key) {
        case SDLK_SPACE:      return ' ';
        case SDLK_MINUS:      return shift ? '_' : '-';
        case SDLK_EQUALS:     return shift ? '+' : '=';
        case SDLK_LEFTBRACKET:  return shift ? '{' : '[';
        case SDLK_RIGHTBRACKET: return shift ? '}' : ']';
        case SDLK_BACKSLASH:  return shift ? '|' : '\\';
        case SDLK_SEMICOLON:  return shift ? ':' : ';';
        case SDLK_QUOTE:      return shift ? '"' : '\'';
        case SDLK_BACKQUOTE:  return shift ? '~' : '`';
        case SDLK_COMMA:      return shift ? '<' : ',';
        case SDLK_PERIOD:     return shift ? '>' : '.';
        case SDLK_SLASH:      return shift ? '?' : '/';
        default: return 0;  // Not a printable character
    }
}

// Map Amiga keycodes (AK_*) to SDL keycodes for GUI navigation
// This is a subset of common keys needed for GUI
static SDL_Keycode amiga_to_sdl_key(int ak_code) {
    switch (ak_code) {
        // Letters
        case 0x20: return SDLK_a;  // AK_A
        case 0x35: return SDLK_b;  // AK_B
        case 0x33: return SDLK_c;  // AK_C
        case 0x22: return SDLK_d;  // AK_D
        case 0x12: return SDLK_e;  // AK_E
        case 0x23: return SDLK_f;  // AK_F
        case 0x24: return SDLK_g;  // AK_G
        case 0x25: return SDLK_h;  // AK_H
        case 0x17: return SDLK_i;  // AK_I
        case 0x26: return SDLK_j;  // AK_J
        case 0x27: return SDLK_k;  // AK_K
        case 0x28: return SDLK_l;  // AK_L
        case 0x37: return SDLK_m;  // AK_M
        case 0x36: return SDLK_n;  // AK_N
        case 0x18: return SDLK_o;  // AK_O
        case 0x19: return SDLK_p;  // AK_P
        case 0x10: return SDLK_q;  // AK_Q
        case 0x13: return SDLK_r;  // AK_R
        case 0x21: return SDLK_s;  // AK_S
        case 0x14: return SDLK_t;  // AK_T
        case 0x16: return SDLK_u;  // AK_U
        case 0x34: return SDLK_v;  // AK_V
        case 0x11: return SDLK_w;  // AK_W
        case 0x32: return SDLK_x;  // AK_X
        case 0x15: return SDLK_y;  // AK_Y
        case 0x31: return SDLK_z;  // AK_Z
        
        // Numbers
        case 0x0A: return SDLK_0;  // AK_0
        case 0x01: return SDLK_1;  // AK_1
        case 0x02: return SDLK_2;  // AK_2
        case 0x03: return SDLK_3;  // AK_3
        case 0x04: return SDLK_4;  // AK_4
        case 0x05: return SDLK_5;  // AK_5
        case 0x06: return SDLK_6;  // AK_6
        case 0x07: return SDLK_7;  // AK_7
        case 0x08: return SDLK_8;  // AK_8
        case 0x09: return SDLK_9;  // AK_9
        
        // Special keys
        case 0x40: return SDLK_SPACE;     // AK_SPC
        case 0x44: return SDLK_RETURN;    // AK_RET
        case 0x41: return SDLK_BACKSPACE; // AK_BS
        case 0x46: return SDLK_DELETE;    // AK_DEL
        case 0x45: return SDLK_ESCAPE;    // AK_ESC
        case 0x42: return SDLK_TAB;       // AK_TAB
        
        // Arrow keys
        case 0x4C: return SDLK_UP;        // AK_UP
        case 0x4D: return SDLK_DOWN;      // AK_DN
        case 0x4F: return SDLK_LEFT;      // AK_LF
        case 0x4E: return SDLK_RIGHT;     // AK_RT
        
        // Modifiers
        case 0x60: return SDLK_LSHIFT;    // AK_LSH
        case 0x61: return SDLK_RSHIFT;    // AK_RSH
        case 0x63: return SDLK_LCTRL;     // AK_CTRL
        case 0x64: return SDLK_LALT;      // AK_LALT
        case 0x65: return SDLK_RALT;      // AK_RALT
        case 0x66: return SDLK_LGUI;      // AK_LAMI
        case 0x67: return SDLK_RGUI;      // AK_RAMI
        
        // Function keys
        case 0x50: return SDLK_F1;        // AK_F1
        case 0x51: return SDLK_F2;        // AK_F2
        case 0x52: return SDLK_F3;        // AK_F3
        case 0x53: return SDLK_F4;        // AK_F4
        case 0x54: return SDLK_F5;        // AK_F5
        case 0x55: return SDLK_F6;        // AK_F6
        case 0x56: return SDLK_F7;        // AK_F7
        case 0x57: return SDLK_F8;        // AK_F8
        case 0x58: return SDLK_F9;        // AK_F9
        case 0x59: return SDLK_F10;       // AK_F10
        
        // Punctuation
        case 0x0B: return SDLK_MINUS;           // AK_MINUS
        case 0x0C: return SDLK_EQUALS;          // AK_EQUAL
        case 0x0D: return SDLK_BACKSLASH;       // AK_BACKSLASH
        case 0x1A: return SDLK_LEFTBRACKET;     // AK_LBRACKET
        case 0x1B: return SDLK_RIGHTBRACKET;    // AK_RBRACKET
        case 0x29: return SDLK_SEMICOLON;       // AK_SEMICOLON
        case 0x2A: return SDLK_QUOTE;           // AK_QUOTE
        case 0x00: return SDLK_BACKQUOTE;       // AK_BACKQUOTE
        case 0x38: return SDLK_COMMA;           // AK_COMMA
        case 0x39: return SDLK_PERIOD;          // AK_PERIOD
        case 0x3A: return SDLK_SLASH;           // AK_SLASH
        case 0x4b: return SDLK_F11;            // F11
        case 0x6f: return SDLK_F12;            // F12

        default: return SDLK_UNKNOWN;
    }
}

// JNI function: Set Amiga keyboard key state (for VirtualKeyboardOverlay)
extern "C" JNIEXPORT void JNICALL
Java_com_blitterstudio_amiberry_VirtualKeyboardOverlay_nativeSetKey(
    JNIEnv* env, jobject thiz,
    jint keyCode, jboolean pressed)
{
    SDL_Log("VKeyboard JNI: keyCode=0x%02x pressed=%d gui_running=%d", keyCode, pressed, gui_running);

    if (gui_running) {
        SDL_Keycode sdlKey = amiga_to_sdl_key(keyCode);
        if (sdlKey == SDLK_UNKNOWN) return;
        
        // Track shift state
        if (sdlKey == SDLK_LSHIFT || sdlKey == SDLK_RSHIFT) {
            s_shift_active = pressed;
            push_sdl_key(sdlKey, pressed);  // Still send key event for modifiers
            return;
        }
        
        // For printable characters, send SDL_TEXTINPUT on key press
        if (pressed) {
            char ch = sdl_key_to_char(sdlKey, s_shift_active);
            if (ch != 0) {
                push_sdl_text_input(ch);
                // Don't send SDL_KEYDOWN for printable chars - SDLInput ignores them anyway
                return;
            }
        }
        
        // For non-printable keys (arrows, backspace, delete, etc.), send SDL key events
        push_sdl_key(sdlKey, pressed);
    } else {
        // Send directly to Amiberry's keyboard handler for emulation
        // keyCode is an Amiga scancode (AK_* value)
        inputdevice_do_keyboard(keyCode, pressed ? 1 : 0);
    }
}

// C++ API functions (kept for compatibility)

void android_vjoystick_init(SDL_Renderer* renderer) {
    SDL_Log("android_vjoystick_init: Using Android native overlay");
}

void android_vjoystick_quit() {
    SDL_Log("android_vjoystick_quit: Cleanup (Android native overlay)");
}

void android_vjoystick_render(SDL_Renderer* renderer) {
    // No longer needed - Android overlay renders itself
}

void android_overlay_button_render(SDL_Renderer* renderer) {
    // No longer needed - Android overlay renders itself
}

bool android_vjoystick_handle_touch(const SDL_Event& event) {
    return false;
}

bool android_overlay_button_handle_touch(const SDL_Event& event) {
    return false;
}

AndroidOverlayState android_overlay_get_state() {
    return s_overlay_state;
}

void android_overlay_set_state(AndroidOverlayState state) {
    s_overlay_state = state;
}

void android_overlay_cycle() {
    // No longer needed - Android overlay handles state cycling
}

bool android_vjoystick_is_active() {
    return s_overlay_state == OVERLAY_JOYSTICK;
}

int android_vjoystick_get_device_index() {
    // Find the virtual joystick in the device array
    extern struct didata di_joystick[];
    
    int num_joys = inputdevice_get_device_total(IDTYPE_JOYSTICK);
    for (int i = 0; i < num_joys; i++) {
        if (di_joystick[i].joystick_id == -1000) {  // Special ID for virtual joystick
            return i;
        }
    }
    return -1;
}

void android_vjoystick_set_axis(int axis, int value) {
    if (s_virtual_joystick_device_id < 0) {
        s_virtual_joystick_device_id = android_vjoystick_get_device_index();
        if (s_virtual_joystick_device_id < 0) {
            SDL_Log("VJoystick: Device not found in joystick array");
            return;
        }
    }
    
    // Send axis input through the device mapping system
    // value: -1 (left/up), 0 (center), 1 (right/down)
    // Scale to SDL joystick range: -32767 to 32767
    int scaled_value = value * 32767;
    setjoystickstate(s_virtual_joystick_device_id, axis, scaled_value, 32767);
    SDL_Log("VJoystick: set_axis(device=%d, axis=%d, value=%d)", 
            s_virtual_joystick_device_id, axis, value);
}

void android_vjoystick_set_button(int button, bool pressed) {
    if (s_virtual_joystick_device_id < 0) {
        s_virtual_joystick_device_id = android_vjoystick_get_device_index();
        if (s_virtual_joystick_device_id < 0) {
            SDL_Log("VJoystick: Device not found in joystick array");
            return;
        }
    }
    
    // Send button input through the device mapping system
    setjoybuttonstate(s_virtual_joystick_device_id, button, pressed ? 1 : 0);
    SDL_Log("VJoystick: set_button(device=%d, button=%d, pressed=%d)", 
            s_virtual_joystick_device_id, button, pressed);
}

#endif // __ANDROID__
