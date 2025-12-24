/*
 * serial_stub.cpp - Stub implementations for serial port functions on Android
 *
 * Android doesn't support the same POSIX shared memory APIs used by the
 * full serial implementation. This provides minimal stubs to allow compilation.
 */

#include "sysconfig.h"
#include "sysdeps.h"
#include "options.h"
#include "serial.h"

// Serial port state variables (minimal stubs)
uae_u16 serdat;
int doreadser = 0;
int serstat = 0;

void serial_init(void)
{
	// No-op on Android
}

void serial_exit(void)
{
	// No-op on Android
}

void serial_dtr_off(void)
{
	// No-op on Android
}

void serial_dtr_on(void)
{
	// No-op on Android
}

uae_u16 SERDATR(void)
{
	// Return empty status - no data available
	return 0x2000; // TBE (Transmit Buffer Empty) set
}

void SERPER(uae_u16 w)
{
	// No-op on Android
}

void SERDAT(uae_u16 w)
{
	// No-op on Android
}

uae_u8 serial_writestatus(uae_u8 a, uae_u8 b)
{
	return 0;
}

uae_u8 serial_readstatus(uae_u8 a, uae_u8 b)
{
	return 0;
}

void serial_uartbreak(int v)
{
	// No-op on Android
}

void serial_rbf_change(bool set)
{
	// No-op on Android
}

void serial_flush_buffer(void)
{
	// No-op on Android
}

void serial_hsynchandler(void)
{
	// No-op on Android
}

void serial_rethink(void)
{
	// No-op on Android
}

// NOTE: uaeser_* functions are already defined in parser.cpp
// We only provide stubs for functions NOT in parser.cpp

// enet serial functions - stub implementations
void enet_writeser(uae_u16 w)
{
	// No-op on Android
}

int enet_readseravail(void)
{
	return 0;
}

int enet_readser(uae_u16 *buffer)
{
	return 0;
}

int enet_open(const TCHAR *name)
{
	return 0; // Failure
}

void enet_close(void)
{
	// No-op on Android
}
