/*
 * mp3decoder_stub.cpp - Stub MP3 decoder for Android (no mpg123)
 *
 * On Android, MP3 decoding is disabled. This stub provides empty implementations.
 */

#include "sysconfig.h"
#include "sysdeps.h"

#ifdef __ANDROID__

#include "mp3decoder.h"

// mp3decoder class stub implementation
mp3decoder::mp3decoder()
{
    g_mp3stream = nullptr;
}

mp3decoder::~mp3decoder()
{
    // Nothing to clean up
}

uae_u8* mp3decoder::get(struct zfile *zf, uae_u8 *buffer, int maxsize)
{
    // Return nullptr - no MP3 support on Android
    return nullptr;
}

uae_u32 mp3decoder::getsize(struct zfile *zf)
{
    // Return 0 - no MP3 support on Android
    return 0;
}

#endif /* __ANDROID__ */
