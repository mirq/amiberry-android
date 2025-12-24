/*
 * android_jit.h - JIT memory allocation with W^X compliance for Android
 *
 * Android 10+ enforces W^X (Write XOR Execute) policy - memory cannot be
 * simultaneously writable and executable. This module implements dual-mapping
 * using memfd_create to provide separate RW and RX views of the same physical
 * memory for JIT compilation.
 *
 * Usage:
 *   void* rw_ptr;  // For JIT compiler to write code
 *   void* rx_ptr;  // For CPU to execute code
 *   android_jit_alloc(size, &rw_ptr, &rx_ptr);
 *   // Write JIT code to rw_ptr
 *   // Execute from rx_ptr
 *   android_jit_free(rw_ptr, rx_ptr, size);
 */

#ifndef ANDROID_JIT_H
#define ANDROID_JIT_H

#ifdef __ANDROID__

#include <cstddef>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Allocate dual-mapped JIT memory with W^X compliance.
 *
 * Creates a shared memory region mapped twice:
 * - rw_ptr: Read-Write mapping for the JIT compiler to write code
 * - rx_ptr: Read-Execute mapping for the CPU to execute code
 *
 * Both pointers refer to the same physical memory.
 *
 * @param size    Size in bytes to allocate (will be rounded up to page size)
 * @param rw_ptr  Output: pointer to RW mapping (for writing JIT code)
 * @param rx_ptr  Output: pointer to RX mapping (for executing JIT code)
 * @return        0 on success, -1 on failure (check errno)
 */
int android_jit_alloc(size_t size, void** rw_ptr, void** rx_ptr);

/**
 * Free dual-mapped JIT memory.
 *
 * @param rw_ptr  The RW mapping pointer returned by android_jit_alloc
 * @param rx_ptr  The RX mapping pointer returned by android_jit_alloc
 * @param size    The size that was allocated
 */
void android_jit_free(void* rw_ptr, void* rx_ptr, size_t size);

/**
 * Initialize the Android JIT system.
 * Call once at startup before any JIT allocations.
 *
 * @return 0 on success, -1 on failure
 */
int android_jit_init(void);

/**
 * Shutdown the Android JIT system.
 * Call once at shutdown after all JIT memory is freed.
 */
void android_jit_shutdown(void);

/**
 * Convert an RX (execute) pointer to its corresponding RW (write) pointer.
 * Useful when the JIT compiler needs to patch code at a known execute address.
 *
 * @param rx_ptr  The RX mapping pointer
 * @return        The corresponding RW mapping pointer, or NULL if not found
 */
void* android_jit_rx_to_rw(void* rx_ptr);

/**
 * Convert an RW (write) pointer to its corresponding RX (execute) pointer.
 *
 * @param rw_ptr  The RW mapping pointer
 * @return        The corresponding RX mapping pointer, or NULL if not found
 */
void* android_jit_rw_to_rx(void* rw_ptr);

/**
 * Flush instruction cache for the given range.
 * Must be called after writing JIT code and before executing it.
 *
 * @param start   Start address of the range to flush
 * @param size    Size of the range in bytes
 */
void android_jit_flush_cache(void* start, size_t size);

/**
 * Get the page size for alignment purposes.
 *
 * @return System page size in bytes
 */
size_t android_jit_page_size(void);

#ifdef __cplusplus
}
#endif

#endif /* __ANDROID__ */

#endif /* ANDROID_JIT_H */
